package cn.redture.identity.service.impl;

import cn.redture.common.constants.RedisConstants;
import cn.redture.common.dto.UserPrincipal;
import cn.redture.common.exception.BaseException;
import cn.redture.common.exception.jwt.ExpiredRefreshTokenException;
import cn.redture.common.exception.jwt.InvalidRefreshTokenException;
import cn.redture.common.exception.jwt.InvalidTokenException;
import cn.redture.common.exception.jwt.RevokedRefreshTokenException;
import cn.redture.common.util.IdUtil;
import cn.redture.common.util.JwtUtil;
import cn.redture.identity.pojo.dto.LoginRequest;
import cn.redture.identity.pojo.dto.RegisterRequest;
import cn.redture.identity.pojo.dto.TokenResponse;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.mapper.UserMapper;
import cn.redture.identity.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static cn.redture.common.constants.SystemConstants.USER_DEFAULT_NICKNAME_PREFIX;

@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    public TokenResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .isNull(User::getDeletedAt));
        if (count != null && count > 0) {
            throw new BaseException(HttpStatus.CONFLICT, "用户名已存在", "USERNAME_EXISTS");
        }

        // TODO 校验邮箱格式和密码强度等
        User user = new User();
        user.setUsername(request.getUsername());
        user.setNickname(USER_DEFAULT_NICKNAME_PREFIX + IdUtil.nextId());
        user.setPublicId(IdUtil.nextId());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setVipLevel("FREE");
        user.setAiAnalysisEnabled(false);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.insert(user);

        // 生成 access token 和 refresh token
        String accessToken = generateAccessToken(user);
        String refreshToken = generateAndStoreRefreshToken(user.getId());
        return buildTokenResponse(accessToken, refreshToken);
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .isNull(User::getDeletedAt));
        if (user == null) {
            throw new BaseException(HttpStatus.UNAUTHORIZED, "用户名或密码错误", "BAD_CREDENTIALS");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BaseException(HttpStatus.UNAUTHORIZED, "用户名或密码错误", "BAD_CREDENTIALS");
        }

        String accessToken = generateAccessToken(user);
        String refreshToken = generateAndStoreRefreshToken(user.getId());
        return buildTokenResponse(accessToken, refreshToken);
    }

    @Override
    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        String accessToken = authorizationHeader.substring("Bearer ".length());

        try {
            // 1. 即使令牌过期，也尝试从中解析出 userId
            Long userId = jwtUtil.getUserIdFromExpiredToken(accessToken);

            // 2. 将当前 access token 加入黑名单
            addAccessTokenToBlacklist(accessToken);

            // 3. 从 Redis 中删除对应的 refresh token
            if (userId != null) {
                String key = RedisConstants.AUTH_REFRESH_TOKEN_KEY_PREFIX + userId;
                stringRedisTemplate.delete(key);
            }
        } catch (InvalidTokenException ignored) {
        }
    }

    @Override
    public TokenResponse refreshToken(String expiredAccessToken, String refreshToken) {
        if (expiredAccessToken == null || refreshToken == null) {
            throw new InvalidRefreshTokenException("访问令牌和刷新令牌均不能为空");
        }

        // 1. 从过期的 access token 中解析 uid
        Long uidFromAccessToken;
        try {
            uidFromAccessToken = jwtUtil.getUserIdFromExpiredToken(expiredAccessToken);
        } catch (InvalidTokenException e) {
            throw new InvalidRefreshTokenException("无效的访问令牌");
        }

        // 2. 从 refresh token 中解析 uid 并校验
        Long uidFromRefreshToken;
        try {
            uidFromRefreshToken = jwtUtil.getUserIdFromRefreshToken(refreshToken);
        } catch (InvalidTokenException | ExpiredRefreshTokenException e) {
            throw new InvalidRefreshTokenException("无效或已过期的刷新令牌");
        }

        // 3. 校验两个 token 是否属于同一个用户
        if (!uidFromAccessToken.equals(uidFromRefreshToken)) {
            throw new InvalidRefreshTokenException("令牌不匹配");
        }

        // 4. 从 Redis 中检查 refresh token 是否存在且匹配
        String key = RedisConstants.AUTH_REFRESH_TOKEN_KEY_PREFIX + uidFromRefreshToken;
        String tokenInRedis = stringRedisTemplate.opsForValue().get(key);

        if (tokenInRedis == null) {
            throw new ExpiredRefreshTokenException("会话已过期，请重新登录");
        }
        if (!tokenInRedis.equals(refreshToken)) {
            // 如果不匹配，可能意味着 refresh token 已被盗用并轮换。
            // 出于安全考虑，立即吊销该用户的所有会话。
            stringRedisTemplate.delete(key);
            throw new RevokedRefreshTokenException("会话已失效，请重新登录");
        }

        // 5. 将旧的、已过期的 access token 加入黑名单，防止重放
        addAccessTokenToBlacklist(expiredAccessToken);

        // 6. 验证通过，生成一对全新的 token
        User user = userMapper.selectById(uidFromRefreshToken);
        if (user == null || user.getDeletedAt() != null) {
            throw new RevokedRefreshTokenException("用户状态异常，请重新登录");
        }

        String newAccessToken = generateAccessToken(user);
        String newRefreshToken = generateAndStoreRefreshToken(user.getId());
        return buildTokenResponse(newAccessToken, newRefreshToken);
    }

    private TokenResponse buildTokenResponse(String accessToken, String refreshToken) {
        TokenResponse resp = new TokenResponse();
        resp.setAccess_token(accessToken);
        resp.setRefresh_token(refreshToken);
        resp.setToken_type("Bearer");
        resp.setExpires_in(jwtUtil.getAccessTokenExpirationSeconds());
        return resp;
    }

    /**
     * 将 Access Token 加入 Redis 黑名单
     * @param accessToken 要拉黑的令牌
     */
    private void addAccessTokenToBlacklist(String accessToken) {
        try {
            String jti = jwtUtil.getJtiFromToken(accessToken);
            long remainingSeconds = jwtUtil.getRemainingExpirationSeconds(accessToken);
            if (remainingSeconds > 0) {
                String key = RedisConstants.AUTH_JWT_BLACKLIST_KEY_PREFIX + jti;
                stringRedisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(remainingSeconds));
            }
        } catch (Exception e) {
            // 忽略解析失败的 token，因为它本身就无法通过验证
        }
    }

    private String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", user.getId());
        // claims.put("pid", user.getPublicId() != null ? user.getPublicId() : null);
        claims.put("username", user.getUsername());
        // claims.put("vip", user.getVipLevel());
        return jwtUtil.generateAccessToken(claims);
    }

    private String generateAndStoreRefreshToken(Long userId) {
        long ttlMillis = jwtUtil.getRefreshTokenExpirationSeconds() * 1000L;
        String refreshToken = jwtUtil.generateRefreshToken(userId, ttlMillis);
        String key = RedisConstants.AUTH_REFRESH_TOKEN_KEY_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(
                key,
                refreshToken,
                Duration.ofSeconds(jwtUtil.getRefreshTokenExpirationSeconds())
        );
        return refreshToken;
    }
}
