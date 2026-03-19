package cn.redture.gateway.security.ratelimit.core;

/**
 * 限流消费结果。
 */
public record RateLimitConsumeResult(boolean allowed, long remainingTokens, long retryAfterSeconds, String ruleName) {

    public static RateLimitConsumeResult allowed(long remainingTokens) {
        return new RateLimitConsumeResult(true, Math.max(remainingTokens, 0L), 0L, null);
    }

    public static RateLimitConsumeResult blocked(long retryAfterSeconds, long remainingTokens) {
        return new RateLimitConsumeResult(false, Math.max(remainingTokens, 0L), Math.max(retryAfterSeconds, 1L), null);
    }

    public RateLimitConsumeResult withRuleName(String ruleName) {
        return new RateLimitConsumeResult(this.allowed, this.remainingTokens, this.retryAfterSeconds, ruleName);
    }
}
