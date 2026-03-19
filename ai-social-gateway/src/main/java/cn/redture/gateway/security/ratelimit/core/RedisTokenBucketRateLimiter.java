package cn.redture.gateway.security.ratelimit.core;

import cn.redture.gateway.security.ratelimit.EntryRateLimitProperties;
import cn.redture.gateway.security.ratelimit.redis.RateLimitKeyBuilder;
import cn.redture.gateway.security.ratelimit.redis.RedisLuaScriptExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 Redis + Lua 的令牌桶实现。
 */
@Service
@RequiredArgsConstructor
public class RedisTokenBucketRateLimiter implements RateLimiterAlgorithm {

    private static final long TOKEN_SCALE = 1000L;
    private static final long REQUEST_TOKENS_SCALED = TOKEN_SCALE;

    private final RedisLuaScriptExecutor redisLuaScriptExecutor;

    private final RateLimitKeyBuilder rateLimitKeyBuilder;

    @Override
    public RateLimitConsumeResult consume(EntryRateLimitProperties.Rule rule, String identity) {
        int capacity = Math.max(rule.effectiveCapacity(), 1);
        int refillTokens = Math.max(rule.effectiveRefillTokens(), 1);
        long refillPeriodMs = Math.max(rule.effectiveRefillPeriodMs(), 1L);

        long nowMs = System.currentTimeMillis();
        long capacityScaled = capacity * TOKEN_SCALE;
        long refillTokensScaled = refillTokens * TOKEN_SCALE;
        long ttlMs = calculateTtlMs(capacityScaled, refillTokensScaled, refillPeriodMs);

        String key = rateLimitKeyBuilder.buildTokenBucketKey(rule.getName(), identity);
        List<Long> luaResult = redisLuaScriptExecutor.executeTokenBucket(
                key,
                nowMs,
                capacityScaled,
                refillTokensScaled,
                refillPeriodMs,
                REQUEST_TOKENS_SCALED,
                ttlMs
        );

        boolean allowed = luaResult.get(0) == 1L;
        long remaining = luaResult.get(1);
        long retryAfterSeconds = luaResult.get(2);

        if (allowed) {
            return RateLimitConsumeResult.allowed(remaining);
        }
        return RateLimitConsumeResult.blocked(Math.max(retryAfterSeconds, 1L), remaining);
    }

    private long calculateTtlMs(long capacityScaled, long refillTokensScaled, long refillPeriodMs) {
        long fullRefillMs = (long) Math.ceil((double) capacityScaled * refillPeriodMs / refillTokensScaled);
        long ttlMs = Math.max(fullRefillMs * 2L, refillPeriodMs * 2L);
        return Math.max(ttlMs, 1000L);
    }
}
