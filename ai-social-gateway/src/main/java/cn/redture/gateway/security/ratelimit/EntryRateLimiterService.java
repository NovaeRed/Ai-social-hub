package cn.redture.gateway.security.ratelimit;

import cn.redture.common.util.JwtUtil;
import cn.redture.common.util.IpAddressUtil;
import cn.redture.gateway.security.AuthConstants;
import cn.redture.gateway.security.ratelimit.core.RateLimitConsumeResult;
import cn.redture.gateway.security.ratelimit.core.RedisTokenBucketRateLimiter;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 入口限流核心服务：匹配规则并进行 Redis 计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntryRateLimiterService {

    private static final String KEY_PREFIX = "rl:entry";

    private final StringRedisTemplate stringRedisTemplate;

    private final EntryRateLimitProperties properties;

    private final RedisTokenBucketRateLimiter tokenBucketRateLimiter;

    @Resource
    private JwtUtil jwtUtil;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitConsumeResult check(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return RateLimitConsumeResult.allowed(0L);
        }

        String path = request.getRequestURI();
        if (isExcluded(path)) {
            return RateLimitConsumeResult.allowed(0L);
        }

        List<EntryRateLimitProperties.Rule> activeRules = collectActiveRules(path);
        if (activeRules.isEmpty()) {
            return RateLimitConsumeResult.allowed(0L);
        }

        for (EntryRateLimitProperties.Rule rule : activeRules) {
            String identity = resolveIdentity(request, rule.getStrategy());
            if (identity == null || identity.isBlank()) {
                identity = "unknown";
            }

            RateLimitConsumeResult consumeResult = consume(rule, identity);
            if (!consumeResult.allowed()) {
                log.warn("Entry rate limit blocked: rule={}, path={}, identity={}, current={}",
                        rule.getName(), path, identity, consumeResult.remainingTokens());
                return consumeResult.withRuleName(rule.getName());
            }
        }

        return RateLimitConsumeResult.allowed(0L);
    }

    private RateLimitConsumeResult consume(EntryRateLimitProperties.Rule rule, String identity) {
        EntryRateLimitProperties.RateLimitAlgorithm algorithm =
                rule.getAlgorithm() == null ? EntryRateLimitProperties.RateLimitAlgorithm.TOKEN_BUCKET : rule.getAlgorithm();

        if (algorithm == EntryRateLimitProperties.RateLimitAlgorithm.FIXED_WINDOW) {
            return consumeByFixedWindow(rule, identity);
        }
        return tokenBucketRateLimiter.consume(rule, identity);
    }

    private RateLimitConsumeResult consumeByFixedWindow(EntryRateLimitProperties.Rule rule, String identity) {
        long bucket = Instant.now().getEpochSecond() / Math.max(rule.getWindowSeconds(), 1);
        String key = KEY_PREFIX + ":" + rule.getName() + ":" + identity + ":" + bucket;
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            long ttl = Math.max(rule.getWindowSeconds(), 1) + 2L;
            stringRedisTemplate.expire(key, ttl, TimeUnit.SECONDS);
        }

        int maxRequests = Math.max(rule.getMaxRequests(), 1);
        long currentCount = current == null ? 0L : current;
        if (currentCount > maxRequests) {
            long retryAfter = calculateFixedWindowRetryAfterSeconds(rule);
            return RateLimitConsumeResult.blocked(retryAfter, 0L);
        }

        long remaining = Math.max(0L, maxRequests - currentCount);
        return RateLimitConsumeResult.allowed(remaining);
    }

    private long calculateFixedWindowRetryAfterSeconds(EntryRateLimitProperties.Rule rule) {
        long windowSeconds = Math.max(rule.getWindowSeconds(), 1);
        long current = Instant.now().getEpochSecond();
        long nextBoundary = ((current / windowSeconds) + 1) * windowSeconds;
        return Math.max(nextBoundary - current, 1L);
    }

    private boolean isExcluded(String path) {
        for (String pattern : properties.getExcludePatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private List<EntryRateLimitProperties.Rule> collectActiveRules(String path) {
        List<EntryRateLimitProperties.Rule> active = new ArrayList<>();

        EntryRateLimitProperties.Rule defaultRule = properties.getDefaultRule();
        if (defaultRule != null && pathMatcher.match(defaultRule.getPattern(), path)) {
            active.add(defaultRule);
        }

        for (EntryRateLimitProperties.Rule rule : properties.getRules()) {
            if (pathMatcher.match(rule.getPattern(), path)) {
                active.add(rule);
            }
        }

        return active;
    }

    private String resolveIdentity(HttpServletRequest request, EntryRateLimitProperties.LimitKeyStrategy strategy) {
        return switch (strategy) {
            case USER_OR_IP -> resolveUserId(request).orElseGet(() -> resolveClientIp(request));
            case IP -> resolveClientIp(request);
        };
    }

    private java.util.Optional<String> resolveUserId(HttpServletRequest request) {
        String bearerToken = request.getHeader(AuthConstants.TOKEN_HEADER);
        if (bearerToken == null || !bearerToken.startsWith(AuthConstants.TOKEN_PREFIX)) {
            return java.util.Optional.empty();
        }

        String token = bearerToken.substring(AuthConstants.TOKEN_PREFIX.length());
        try {
            Map<String, Object> claims = jwtUtil.getClaimsFromToken(token);
            Object uid = claims.get("uid");
            return uid == null ? java.util.Optional.empty() : java.util.Optional.of(uid.toString());
        } catch (Exception ignore) {
            return java.util.Optional.empty();
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        return IpAddressUtil.extractClientIp(request, properties.getTrustedProxies());
    }
}
