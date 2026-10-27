package cn.redture.aiEngine.consumer;

import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_DLQ_STREAM_KEY;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_GROUP;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_KEY;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import cn.redture.common.util.JsonUtil;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 统一异常处理中心与延时退避调度器 */
@Slf4j
@Component
public class AiAsyncTaskErrorHandler {

    @Resource private StringRedisTemplate stringRedisTemplate;

    @Value("${ai.async.queue.max-retries:5}")
    private int maxRetries;

    @Value("${ai.async.queue.retry-base-ms:1000}")
    private long retryBaseMs;

    @Value("${ai.async.queue.retry-max-ms:60000}")
    private long retryMaxMs;

    @Value(AI_ASYNC_TASK_STREAM_GROUP)
    private String streamGroup;

    static final String RETRY_KEY = "ai:async:tasks:retry";
    private static final DefaultRedisScript<Long> SCHEDULE =
            new DefaultRedisScript<>(
                    """
                    redis.call('ZADD', KEYS[2], ARGV[1], ARGV[2])
                    redis.call('XACK', KEYS[1], ARGV[3], ARGV[4])
                    return 1
                    """,
                    Long.class);
    private static final DefaultRedisScript<Long> PROMOTE =
            new DefaultRedisScript<>(
                    """
                    local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, 50)
                    for _, member in ipairs(due) do
                        local payload = cjson.decode(member)
                        local args = {'*'}
                        for key, value in pairs(payload) do
                            if key ~= '_source_record_id' then
                                table.insert(args, key); table.insert(args, value)
                            end
                        end
                        redis.call('XADD', KEYS[2], unpack(args))
                        redis.call('ZREM', KEYS[1], member)
                    end
                    return #due
                    """,
                    Long.class);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread thread = new Thread(r, "ai-durable-retry-promoter");
                        thread.setDaemon(true);
                        return thread;
                    });

    @PostConstruct
    public void startRetryPromotion() {
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        promoteDueRetries();
                    } catch (Exception e) {
                        log.warn("持久化重试提升失败，保留 Redis 中的任务等待恢复", e);
                    }
                },
                100,
                100,
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stopRetryPromotion() {
        scheduler.shutdownNow();
    }

    void promoteDueRetries() {
        stringRedisTemplate.execute(
                PROMOTE,
                List.of(RETRY_KEY, AI_ASYNC_TASK_STREAM_KEY),
                String.valueOf(System.currentTimeMillis()));
    }

    /** Persist the disposition before ACK; delayed work survives an application restart. */
    public void handleFailure(
            MapRecord<String, Object, Object> record,
            String domain,
            String taskJson,
            int retryCount,
            String createdAtEpoch,
            String bizId,
            String traceId,
            Exception exception) {
        int nextRetry = retryCount + 1;
        String errorCode = resolveErrorCode(exception);
        Map<String, String> payload = new HashMap<>();
        record.getValue()
                .forEach(
                        (key, value) -> {
                            if (key != null && value != null)
                                payload.put(String.valueOf(key), String.valueOf(value));
                        });
        payload.put("task", taskJson);
        payload.put("domain", domain);
        payload.put("biz_id", bizId);
        payload.put("trace_id", traceId);
        payload.put("created_at_epoch", createdAtEpoch);
        payload.put("retry_count", String.valueOf(nextRetry));
        payload.put("error_code", errorCode);
        payload.put("last_error", sanitizeError(exception == null ? null : exception.getMessage()));

        if (!isRetryable(exception, errorCode) || nextRetry > Math.max(maxRetries, 0)) {
            payload.put("failed_at_epoch", String.valueOf(OffsetDateTime.now().toEpochSecond()));
            RedisStreamTransfers.transfer(
                    stringRedisTemplate,
                    AI_ASYNC_TASK_STREAM_KEY,
                    streamGroup,
                    record.getId().getValue(),
                    AI_ASYNC_TASK_DLQ_STREAM_KEY,
                    payload);
            return;
        }
        long dueAt = System.currentTimeMillis() + computeBackoffMs(nextRetry);
        payload.put("requeue_at_epoch_ms", String.valueOf(dueAt));
        payload.put("_source_record_id", record.getId().getValue());
        Long persisted =
                stringRedisTemplate.execute(
                        SCHEDULE,
                        List.of(AI_ASYNC_TASK_STREAM_KEY, RETRY_KEY),
                        String.valueOf(dueAt),
                        JsonUtil.toJson(payload),
                        streamGroup,
                        record.getId().getValue());
        if (persisted == null || persisted != 1L) {
            throw new IllegalStateException("Redis did not persist the retry disposition");
        }
    }

    private String resolveErrorCode(Exception e) {
        BaseException baseException = findBaseException(e);
        if (baseException != null
                && baseException.getErrorCode() != null
                && !baseException.getErrorCode().isBlank()) {
            return baseException.getErrorCode();
        }

        if (e == null) {
            return ErrorCodes.UNKNOWN_ERROR;
        }
        if (e instanceof IllegalArgumentException) {
            return ErrorCodes.INVALID_ARGUMENT;
        }
        String msg = e.getMessage();
        if (msg != null && msg.toLowerCase().contains("timeout")) {
            return ErrorCodes.TIMEOUT;
        }
        if (msg != null && (msg.contains("429") || msg.toLowerCase().contains("rate"))) {
            return ErrorCodes.RATE_LIMITED;
        }
        if (msg != null
                && (msg.contains("503") || msg.toLowerCase().contains("service unavailable"))) {
            return ErrorCodes.UPSTREAM_UNAVAILABLE;
        }
        return ErrorCodes.INTERNAL_ERROR;
    }

    private boolean isRetryable(Exception e, String errorCode) {
        BaseException baseException = findBaseException(e);
        if (baseException != null) {
            if (baseException.getCode() == 429) {
                return true;
            }
            return baseException.getCode() >= 500;
        }

        if (ErrorCodes.INVALID_ARGUMENT.equals(errorCode)
                || ErrorCodes.INVALID_INPUT.equals(errorCode)) {
            return false;
        }
        return !(e instanceof IllegalStateException);
    }

    private BaseException findBaseException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BaseException baseException) {
                return baseException;
            }
            current = current.getCause();
        }
        return null;
    }

    private long computeBackoffMs(int retryCount) {
        long base = Math.max(retryBaseMs, 100L);
        long max = Math.max(retryMaxMs, base);
        long candidate = base;
        for (int i = 1; i < retryCount; i++) {
            candidate = candidate * 2;
            if (candidate >= max) {
                return max;
            }
        }
        return candidate;
    }

    private String sanitizeError(String error) {
        if (error == null || error.isBlank()) {
            return "unknown";
        }
        return error.length() > 512 ? error.substring(0, 512) : error;
    }
}
