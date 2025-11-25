package cn.redture.identity.util;

import cn.redture.common.constants.RedisConstants;
import cn.redture.common.util.JwtUtil;
import cn.redture.identity.pojo.dto.TokenResponseDTO;
import cn.redture.identity.pojo.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Token 管理工具类
 * 负责处理 Token 黑名单和刷新 Token 的失效
 */
@Slf4j
@Component
public class TokenManagementUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtil jwtUtil;

    public void addAccessTokenToBlacklist(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            String jti = jwtUtil.getJtiFromToken(accessToken);
            long remainingSeconds = jwtUtil.getRemainingExpirationSeconds(accessToken);

            // 只将尚未自然过期的 token 加入黑名单
            if (remainingSeconds > 0) {
                String key = RedisConstants.AUTH_JWT_BLACKLIST_KEY_PREFIX + jti;
                stringRedisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(remainingSeconds));
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isAccessTokenInBlacklisted(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return false;
        }
        String jti = jwtUtil.getJtiFromToken(accessToken);
        String key = RedisConstants.AUTH_JWT_BLACKLIST_KEY_PREFIX + jti;
        return stringRedisTemplate.hasKey(key);
    }

    public void deleteRefreshToken(Long userId) {
        String key = RedisConstants.AUTH_REFRESH_TOKEN_KEY_PREFIX + userId;
        stringRedisTemplate.delete(key);
    }

    public TokenResponseDTO buildTokenResponse(String accessToken, String refreshToken) {
        TokenResponseDTO resp = new TokenResponseDTO();
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        resp.setTokenType("Bearer");
        resp.setExpiresIn(jwtUtil.getAccessTokenExpirationSeconds());
        return resp;
    }

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", user.getId());
        // claims.put("pid", user.getPublicId() != null ? user.getPublicId() : null);
        claims.put("username", user.getUsername());
        // claims.put("vip", user.getVipLevel());
        return jwtUtil.generateAccessToken(claims);
    }

    public String generateAndStoreRefreshToken(Long userId) {
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

