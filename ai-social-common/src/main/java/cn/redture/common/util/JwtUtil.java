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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

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

    // 从配置文件中读取JWT的过期时间（单位：毫秒），默认为1天
    @Value("${jwt.expiration:86400000}")
    private long expiration;

    private SecretKey secretKey;

    /**
     * 初始化方法，在依赖注入后执行，用于生成密钥
     */
    @PostConstruct
    private void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 根据用户ID生成一个JWT
     *
     * @param userId 用户ID
     * @return 生成的JWT字符串
     */
    public String generateToken(String userId) {
        return generateToken(Map.of("uid", userId));
    }

    /**
     * 根据自定义的claims生成一个JWT
     *
     * @param claims 包含在token中的自定义数据
     * @return 生成的JWT字符串
     */
    public String generateToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
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
     * 验证JWT是否有效且未过期
     *
     * @param token JWT字符串
     * @return 如果token有效且未过期，则返回true
     */
    public boolean validateToken(String token) {
        try {
            // getClaimsFromToken会处理所有解析和验证逻辑，如果没抛异常就代表有效
            getClaimsFromToken(token);
            return true;
        } catch (BaseException e) {
            // 捕获我们自定义的异常，说明token无效或过期
            return false;
        }
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
}
