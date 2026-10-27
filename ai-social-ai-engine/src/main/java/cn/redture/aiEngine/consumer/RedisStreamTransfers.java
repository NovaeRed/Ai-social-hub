package cn.redture.aiEngine.consumer;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis holds the successor before acknowledging the source; duplicate execution remains possible.
 */
final class RedisStreamTransfers {
    private static final DefaultRedisScript<String> TRANSFER =
            new DefaultRedisScript<>(
                    """
                    local args = {'*'}
                    for i = 3, #ARGV do table.insert(args, ARGV[i]) end
                    local id = redis.call('XADD', KEYS[2], unpack(args))
                    redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
                    return id
                    """,
                    String.class);

    static void transfer(
            StringRedisTemplate redis,
            String source,
            String group,
            String recordId,
            String destination,
            Map<String, String> payload) {
        List<Object> args = new ArrayList<>();
        args.add(group);
        args.add(recordId);
        payload.forEach(
                (key, value) -> {
                    args.add(key);
                    args.add(value);
                });
        if (redis.execute(TRANSFER, List.of(source, destination), args.toArray()) == null) {
            throw new IllegalStateException("Redis did not persist the stream disposition");
        }
    }

    private RedisStreamTransfers() {}
}
