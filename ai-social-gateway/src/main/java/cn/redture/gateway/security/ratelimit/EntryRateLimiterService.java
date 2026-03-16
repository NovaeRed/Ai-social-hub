package cn.redture.gateway.security.ratelimit;

import cn.redture.common.util.JwtUtil;
import cn.redture.gateway.security.AuthConstants;
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

    @Resource
    private JwtUtil jwtUtil;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public Decision check(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return Decision.allow();
        }

        String path = request.getRequestURI();
        if (isExcluded(path)) {
            return Decision.allow();
        }

        List<EntryRateLimitProperties.Rule> activeRules = collectActiveRules(path);
        if (activeRules.isEmpty()) {
            return Decision.allow();
        }

        for (EntryRateLimitProperties.Rule rule : activeRules) {
            String identity = resolveIdentity(request, rule.getStrategy());
            if (identity == null || identity.isBlank()) {
                identity = "unknown";
            }

            long bucket = Instant.now().getEpochSecond() / Math.max(rule.getWindowSeconds(), 1);
            String key = KEY_PREFIX + ":" + rule.getName() + ":" + identity + ":" + bucket;
            Long current = stringRedisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                long ttl = Math.max(rule.getWindowSeconds(), 1) + 2L;
                stringRedisTemplate.expire(key, ttl, TimeUnit.SECONDS);
            }

            if (current != null && current > rule.getMaxRequests()) {
                log.warn("Entry rate limit blocked: rule={}, path={}, identity={}, current={}",
                        rule.getName(), path, identity, current);
                return Decision.blocked(rule);
            }
        }

        return Decision.allow();
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
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    public record Decision(boolean blocked, EntryRateLimitProperties.Rule rule) {

        public static Decision allow() {
            return new Decision(false, null);
        }

        public static Decision blocked(EntryRateLimitProperties.Rule rule) {
            return new Decision(true, rule);
        }
    }
}
