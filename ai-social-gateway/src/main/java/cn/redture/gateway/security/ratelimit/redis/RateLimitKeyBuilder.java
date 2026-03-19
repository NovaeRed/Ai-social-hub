package cn.redture.gateway.security.ratelimit.redis;

import org.springframework.stereotype.Component;

/**
 * 限流 Redis Key 构建器。
 */
@Component
public class RateLimitKeyBuilder {

    private static final String ENTRY_TOKEN_BUCKET_PREFIX = "rl:entry:tb";

    public String buildTokenBucketKey(String ruleName, String identity) {
        String safeRuleName = (ruleName == null || ruleName.isBlank()) ? "default" : ruleName;
        String safeIdentity = (identity == null || identity.isBlank()) ? "unknown" : identity;
        return ENTRY_TOKEN_BUCKET_PREFIX + ":" + safeRuleName + ":" + safeIdentity;
    }
}
