package cn.redture.aiEngine.consumer;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_DLQ_STREAM_KEY;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_KEY;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_GROUP;

/**
 * 统一异常处理中心与延时退避调度器
 */
@Slf4j
@Component
public class AiAsyncTaskErrorHandler {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${ai.async.queue.max-retries:5}")
    private int maxRetries;

    @Value("${ai.async.queue.retry-base-ms:1000}")
    private long retryBaseMs;

    @Value("${ai.async.queue.retry-max-ms:60000}")
    private long retryMaxMs;

    @Value(AI_ASYNC_TASK_STREAM_GROUP)
    private String streamGroup;

    // 非阻塞的调度线程池，专门用来把需要重试的任务重新放入 Stream
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * 消费失败后的重试或入DLQ处理
     */
    public void handleFailure(MapRecord<String, Object, Object> record, String domain, String taskJson, int retryCount, String createdAtEpoch, String bizId, String traceId, Exception exception) {
        int nextRetry = retryCount + 1;
        String errorCode = resolveErrorCode(exception);
        boolean retryable = isRetryable(exception, errorCode);
        String errorMessage = sanitizeError(exception == null ? null : exception.getMessage());

        if (!retryable || nextRetry > Math.max(maxRetries, 0)) {
            Map<String, String> dlq = new HashMap<>();
            dlq.put("task", taskJson);
            dlq.put("domain", domain);
            dlq.put("biz_id", bizId);
            dlq.put("trace_id", traceId);
            dlq.put("created_at", createdAtEpoch);
            dlq.put("retry_count", String.valueOf(nextRetry));
            dlq.put("error_code", errorCode);
            dlq.put("last_error", errorMessage);
            dlq.put("failed_at_epoch", String.valueOf(OffsetDateTime.now().toEpochSecond()));
            stringRedisTemplate.opsForStream().add(StreamRecords.string(dlq).withStreamKey(AI_ASYNC_TASK_DLQ_STREAM_KEY));
            acknowledge(record);
            return;
        }

        long backoffMs = computeBackoffMs(nextRetry);

        // 异步非阻塞退避：计算好时间后，交给后台调度器，到达时间了再发送给 Redis
        scheduler.schedule(() -> {
            try {
                Map<String, String> retryPayload = new HashMap<>();
                retryPayload.put("domain", domain);
                retryPayload.put("task", taskJson);
                retryPayload.put("biz_id", bizId);
                retryPayload.put("trace_id", traceId);
                retryPayload.put("created_at", createdAtEpoch);
                retryPayload.put("retry_count", String.valueOf(nextRetry));
                retryPayload.put("error_code", errorCode);
                retryPayload.put("last_error", errorMessage);
                retryPayload.put("requeue_at_epoch", String.valueOf(OffsetDateTime.now().toEpochSecond()));
                stringRedisTemplate.opsForStream().add(StreamRecords.string(retryPayload).withStreamKey(AI_ASYNC_TASK_STREAM_KEY));
            } catch(Exception e) {
                log.error("延时重试队列入队失败", e);
            }
        }, backoffMs, TimeUnit.MILLISECONDS);

        // 原本的记录先 ack，以防阻塞后续的拉取。
        acknowledge(record);
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        stringRedisTemplate.opsForStream().acknowledge(AI_ASYNC_TASK_STREAM_KEY, streamGroup, record.getId());
    }

    private String resolveErrorCode(Exception e) {
        BaseException baseException = findBaseException(e);
        if (baseException != null && baseException.getErrorCode() != null && !baseException.getErrorCode().isBlank()) {
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
        if (msg != null && (msg.contains("503") || msg.toLowerCase().contains("service unavailable"))) {
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

        if (ErrorCodes.INVALID_ARGUMENT.equals(errorCode) || ErrorCodes.INVALID_INPUT.equals(errorCode)) {
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