package cn.redture.gateway.security.ratelimit.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Redis Lua 脚本执行器。
 */
@Component
public class RedisLuaScriptExecutor {

    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<List> tokenBucketScript;

    public RedisLuaScriptExecutor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setLocation(new ClassPathResource("lua/entry_token_bucket.lua"));
        this.tokenBucketScript.setResultType(List.class);
    }

    public List<Long> executeTokenBucket(String key, long nowMs, long capacityScaled, long refillTokensScaled, long refillPeriodMs, long requestTokensScaled, long ttlMs) {
        List<?> rawResult = stringRedisTemplate.execute(
                tokenBucketScript,
                Collections.singletonList(key),
                String.valueOf(nowMs),
                String.valueOf(capacityScaled),
                String.valueOf(refillTokensScaled),
                String.valueOf(refillPeriodMs),
                String.valueOf(requestTokensScaled),
                String.valueOf(ttlMs)
        );

        if (rawResult == null || rawResult.size() < 3) {
            return List.of(0L, 0L, 1L);
        }

        return List.of(
                toLong(rawResult.get(0)),
                toLong(rawResult.get(1)),
                toLong(rawResult.get(2))
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
