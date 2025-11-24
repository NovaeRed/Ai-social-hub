package cn.redture.identity.service.impl;

import cn.redture.common.constants.RedisConstants;
import cn.redture.common.exception.BaseException;
import cn.redture.common.exception.jwt.ExpiredRefreshTokenException;
import cn.redture.common.exception.jwt.InvalidRefreshTokenException;
import cn.redture.common.exception.jwt.InvalidTokenException;
import cn.redture.common.exception.jwt.RevokedRefreshTokenException;
import cn.redture.common.util.JwtUtil;
import cn.redture.identity.dto.LoginRequest;
import cn.redture.identity.dto.RegisterRequest;
import cn.redture.identity.dto.TokenResponse;
import cn.redture.identity.entity.User;
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

    private static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 15 * 60; // 15 minutes

    @Override
    public TokenResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .isNull(User::getDeletedAt));
        if (count != null && count > 0) {
            throw new BaseException(HttpStatus.CONFLICT, "用户名已存在", "USERNAME_EXISTS");
        }

        // 构造并持久化用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setNickname(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setVipLevel("FREE");
        user.setAiAnalysisEnabled(false);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.insert(user);

        // 生成 access token（包含用户 claims）
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
    public void logout() {
        // 从安全上下文中获取当前登录用户 ID
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return;
        }
        String principal = authentication.getName();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, principal)
                .isNull(User::getDeletedAt));
        if (user == null) {
            return;
        }
        String key = RedisConstants.AUTH_REFRESH_TOKEN_KEY_PREFIX + user.getId();
        stringRedisTemplate.delete(key);
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("刷新令牌不能为空");
        }

        // 1. 解析 refresh token，校验签名/过期时间，并提取 uid
        Long userId;
        try {
            userId = jwtUtil.getUserIdFromRefreshToken(refreshToken);
        } catch (InvalidTokenException | ExpiredRefreshTokenException e) {
            throw new InvalidRefreshTokenException("无效的刷新令牌");
        }

        // 2. 从 Redis 中检查是否存在且匹配
        String key = RedisConstants.AUTH_REFRESH_TOKEN_KEY_PREFIX + userId;
        String tokenInRedis = stringRedisTemplate.opsForValue().get(key);

        if (tokenInRedis == null) {
            // Redis 中已无记录，视为过期
            throw new ExpiredRefreshTokenException("刷新令牌已过期，请重新登录");
        }

        if (!tokenInRedis.equals(refreshToken)) {
            // 存在但与当前传入不一致，说明被替换或撤销
            throw new RevokedRefreshTokenException("刷新令牌已失效，请重新登录");
        }

        // 3. 重新加载用户信息，签发新的 access token 和 refresh token
        User user = userMapper.selectById(userId);
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
        resp.setExpires_in(ACCESS_TOKEN_EXPIRES_IN_SECONDS);
        return resp;
    }

    private String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", user.getId());
        claims.put("pid", user.getPublicId() != null ? user.getPublicId().toString() : null);
        claims.put("username", user.getUsername());
        claims.put("vip", user.getVipLevel());
        return jwtUtil.generateToken(claims);
    }

    private String generateAndStoreRefreshToken(Long userId) {
        long ttlMillis = RedisConstants.AUTH_REFRESH_TOKEN_TTL_SECONDS * 1000L;
        String refreshToken = jwtUtil.generateRefreshToken(userId, ttlMillis);
        String key = RedisConstants.AUTH_REFRESH_TOKEN_KEY_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(
                key,
                refreshToken,
                Duration.ofSeconds(RedisConstants.AUTH_REFRESH_TOKEN_TTL_SECONDS)
        );
        return refreshToken;
    }
}
