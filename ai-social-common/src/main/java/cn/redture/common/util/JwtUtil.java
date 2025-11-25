package cn.redture.common.util;

import cn.redture.common.exception.BaseException;
import cn.redture.common.exception.jwt.ExpiredTokenException;
import cn.redture.common.exception.jwt.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT (JSON Web Token) 工具类
 * 负责生成、解析和验证JWT
 */
@Component
@Slf4j
public class JwtUtil {

    // 从配置文件中读取JWT密钥，提供一个较长的默认值以确保安全性
    @Value("${jwt.secret:default_secret_key_that_is_long_enough_to_be_secure_with_hs256}")
    private String secret;

    // 从配置文件中读取JWT过期时间（单位：秒），默认为15分钟
    @Getter
    @Value("${jwt.access_token_expiration:900}")
    private long accessTokenExpirationSeconds;

    // 从配置文件中读取JWT刷新令牌过期时间（单位：秒），默认为7天
    @Getter
    @Value("${jwt.refresh_token_expiration_seconds:604800}")
    private long refreshTokenExpirationSeconds;

    private SecretKey secretKey;

    /**
     * 初始化方法，在依赖注入后执行，用于生成密钥
     */
    @PostConstruct
    private void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 根据自定义的claims和过期时间生成一个JWT
     *
     * @param claims           包含在token中的自定义数据
     * @param expiresInSeconds 过期时间（秒）
     * @return 生成的JWT字符串
     */
    public String generateToken(Map<String, Object> claims, long expiresInSeconds) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiresInSeconds * 1000);

        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString()) // 添加 JTI
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 根据自定义的claims生成一个JWT，使用默认过期时间
     *
     * @param claims 包含在token中的自定义数据
     * @return 生成的JWT字符串
     */
    public String generateAccessToken(Map<String, Object> claims) {
        return generateToken(claims, accessTokenExpirationSeconds);
    }

    /**
     * 生成 Refresh Token（JWT）
     */
    public String generateRefreshToken(Long userId, long ttlMillis) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttlMillis);
        return Jwts.builder()
                .claim("uid", userId)
                .claim("typ", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从JWT中解析出所有的Claims
     *
     * @param token JWT字符串
     * @return Claims对象，包含了token中的所有信息
     * @throws ExpiredTokenException 如果token过期
     * @throws InvalidTokenException 如果token无效（签名错误、格式错误等）
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new ExpiredTokenException("Token已过期");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("无效的Token");
        }
    }

    /**
     * 从JWT中解析出用户ID（Subject）
     *
     * @param token JWT字符串
     * @return 用户ID
     */
    public String getUserIdFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    /**
     * 验证JWT是否有效且未过期。如果无效，则抛出相应的异常。
     *
     * @param token JWT字符串
     * @throws ExpiredTokenException 如果token过期
     * @throws InvalidTokenException 如果token无效
     */
    public void validateToken(String token) {
        // getClaimsFromToken会处理所有解析和验证逻辑，如果没抛异常就代表有效
        getClaimsFromToken(token);
    }

    /**
     * 检查JWT是否已过期（注意：此方法仅检查过期时间，不验证签名）
     *
     * @param token JWT字符串
     * @return 如果已过期，返回true
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expirationDate = getClaimsFromToken(token).getExpiration();
            return expirationDate.before(new Date());
        } catch (ExpiredTokenException e) {
            // 如果在解析时就因为过期而抛出异常，那它肯定是过期的
            return true;
        } catch (InvalidTokenException e) {
            // 如果是其他无效token的异常，也认为它无法使用，视为“过期”
            return true;
        }
    }

    /**
     * 从JWT中解析出所有的Claims，忽略过期时间
     *
     * @param token JWT字符串
     * @return Claims对象，包含了token中的所有信息
     * @throws InvalidTokenException 如果token无效（签名错误、格式错误等）
     */
    private Claims getClaimsFromTokenIgnoringExpiration(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("无效的Token");
        }
    }

    /**
     * 从可能已过期的 Access Token 中解析出 uid。
     * 此方法不验证过期时间，但会验证签名。
     */
    public Long getUserIdFromExpiredToken(String accessToken) {
        Claims claims = getClaimsFromTokenIgnoringExpiration(accessToken);
        Object uidObj = claims.get("uid");
        if (uidObj == null) {
            throw new InvalidTokenException("令牌缺少用户ID");
        }
        try {
            return Long.valueOf(uidObj.toString());
        } catch (NumberFormatException e) {
            throw new InvalidTokenException("令牌中的用户ID格式错误");
        }
    }

    /**
     * 从 Refresh Token 中解析出 uid，并校验 typ=refresh。
     */
    public Long getUserIdFromRefreshToken(String refreshToken) {
        Claims claims = getClaimsFromToken(refreshToken);
        Object typ = claims.get("typ");
        if (typ == null || !"refresh".equals(typ.toString())) {
            throw new InvalidTokenException("无效的刷新令牌类型");
        }
        Object uidObj = claims.get("uid");
        if (uidObj == null) {
            throw new InvalidTokenException("刷新令牌缺少用户ID");
        }
        try {
            return Long.valueOf(uidObj.toString());
        } catch (NumberFormatException e) {
            throw new InvalidTokenException("刷新令牌中的用户ID格式错误");
        }
    }

    /**
     * 从 token 中获取 JTI (JWT ID)
     * @param token JWT
     * @return JTI
     */
    public String getJtiFromToken(String token) {
        return getClaimsFromTokenIgnoringExpiration(token).getId();
    }

    /**
     * 计算 token 剩余的有效时间（秒）
     * @param token JWT
     * @return 剩余有效时间（秒），如果已过期或无效则返回 0
     */
    public long getRemainingExpirationSeconds(String token) {
        try {
            Date expiration = getClaimsFromToken(token).getExpiration();
            long remainingMillis = expiration.getTime() - System.currentTimeMillis();
            return remainingMillis > 0 ? remainingMillis / 1000 : 0;
        } catch (BaseException e) {
            return 0;
        }
    }
}
