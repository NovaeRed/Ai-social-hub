package cn.redture.aiEngine.consumer;

import cn.redture.aiEngine.pojo.dto.AiAsyncTaskDTO;
import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;
import cn.redture.common.event.AiTaskCompletedEvent;
import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import cn.redture.aiEngine.service.AsyncTaskAuditService;
import cn.redture.aiEngine.service.AiConfigService;
import cn.redture.aiEngine.service.AiTaskExecutionService;
import cn.redture.common.event.internal.AiAsyncTaskEvent;
import org.springframework.context.ApplicationEventPublisher;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.exception.BaseException;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.util.TraceContext;
import cn.redture.common.util.JsonUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_DLQ_STREAM_KEY;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_DLQ_STREAM_GROUP;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_GROUP;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_KEY;

/**
 * 统一异步任务 Streams 消费者。
 */
@Slf4j
@Component
public class AiAsyncTaskStreamConsumer implements SmartLifecycle {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiTaskExecutionService aiTaskExecutionService;

    @Resource
    private AiConfigService aiConfigService;

    @Resource
    private AsyncTaskAuditService asyncTaskAuditService;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    private Thread consumerThread;
    private Thread dlqThread;
    private volatile boolean running;

    @Value("${ai.async.consumer.enabled:true}")
    private boolean consumerEnabled;

    @Value(AI_ASYNC_TASK_STREAM_GROUP)
    private String streamGroup;

    @Value("${spring.application.name:ai-social-app}")
    private String consumerName;

    @Value("${ai.async.queue.block-ms:5000}")
    private long blockMs;

    @Value("${ai.async.queue.batch-size:10}")
    private int batchSize;

    @Value("${ai.async.queue.max-retries:5}")
    private int maxRetries;

    @Value("${ai.async.queue.retry-base-ms:1000}")
    private long retryBaseMs;

    @Value("${ai.async.queue.retry-max-ms:60000}")
    private long retryMaxMs;

    @Value("${ai.async.dlq.consumer.enabled:true}")
    private boolean dlqConsumerEnabled;

    @Value("${ai.async.dlq.notification-max-replay:3}")
    private int notificationMaxDlqReplay;

    @Override
    public void start() {
        if (running) {
            return;
        }
        if (!consumerEnabled) {
            log.debug("统一异步 Streams 消费者已禁用 (ai.async.consumer.enabled=false)");
            return;
        }

        running = true;

        ensureStreamGroup();
        ensureDlqStreamGroup();

        consumerThread = new Thread(this::consumeLoop, "ai-async-stream-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("统一异步 Streams 消费者线程已启动，group={}, consumer={}", streamGroup, consumerName);

        if (dlqConsumerEnabled) {
            dlqThread = new Thread(this::consumeDlqLoop, "ai-async-dlq-processor");
            dlqThread.setDaemon(true);
            dlqThread.start();
            log.info("统一异步 DLQ 处置线程已启动，group={}", AI_ASYNC_TASK_DLQ_STREAM_GROUP);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (consumerThread != null && consumerThread.isAlive()) {
            consumerThread.interrupt();
            joinQuietly(consumerThread, 2000L);
        }
        if (dlqThread != null && dlqThread.isAlive()) {
            dlqThread.interrupt();
            joinQuietly(dlqThread, 2000L);
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        // Use a high phase so this consumer stops earlier in shutdown than lower-phase infra.
        return Integer.MAX_VALUE - 100;
    }

    private void consumeLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(streamGroup, consumerName),
                        StreamReadOptions.empty().count(Math.max(batchSize, 1)).block(Duration.ofMillis(Math.max(blockMs, 1000))),
                        StreamOffset.create(AI_ASYNC_TASK_STREAM_KEY, ReadOffset.lastConsumed())
                );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    processRecord(record);
                }
            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted() || isRedisStoppedException(e)) {
                    log.debug("统一异步 Streams 消费线程停止中，结束消费循环");
                    break;
                }
                log.error("消费统一异步 Streams 任务时发生异常", e);
            }
        }
    }

    /**
     * 处理单条主队列消息。
     *
     * @param record Redis Stream 记录
     */
    private void processRecord(MapRecord<String, Object, Object> record) {
        String domain = asString(record.getValue().get("domain"));
        String taskJson = asString(record.getValue().get("task"));
        int retryCount = parseRetryCount(record.getValue().get("retry_count"));
        String bizId = firstNonBlank(asString(record.getValue().get("biz_id")), record.getId().getValue());
        String traceId = firstNonBlank(asString(record.getValue().get("trace_id")), UUID.randomUUID().toString());
        String createdAt = firstNonBlank(asString(record.getValue().get("created_at_epoch")), String.valueOf(OffsetDateTime.now().toEpochSecond()));
        Long envelopeUserId = null;
        try {
            String uStr = asString(record.getValue().get("user_id"));
            if (uStr != null) envelopeUserId = Long.valueOf(uStr);
        } catch (NumberFormatException ignored) {
        }
        String envelopeEventType = asString(record.getValue().get("event_type"));

        if (taskJson == null || taskJson.isBlank()) {
            acknowledge(record);
            return;
        }

        if (domain == null || domain.isBlank()) {
            log.warn("统一异步任务缺少 domain 字段，recordId={}", record.getId());
            acknowledge(record);
            return;
        }

        TraceContext.setTraceId(traceId);
        try {
            dispatchByDomain(domain, envelopeEventType, envelopeUserId, taskJson, record.getId().getValue());
            acknowledge(record);
            asyncTaskAuditService.record("CONSUME_SUCCESS", parseDomain(domain), Map.of(
                    "biz_id", bizId,
                    "trace_id", traceId,
                    "stream_record_id", record.getId().getValue()
            ));
        } catch (Exception e) {
            log.error("处理统一异步任务失败，recordId={}, retryCount={}", record.getId(), retryCount, e);
            retryOrDlq(record, domain, taskJson, retryCount, createdAt, bizId, traceId, e);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 按业务领域分发任务到对应处理器
     *
     * @param domain   任务领域
     * @param taskJson 任务载荷JSON
     * @param recordId Stream记录ID
     */
    private void dispatchByDomain(String domain, String eventType, Long userId, String taskJson, String recordId) {
        eventPublisher.publishEvent(new AiAsyncTaskEvent(this, domain, eventType, userId, taskJson, recordId));
    }

    /**
     * 消费失败后的重试或入DLQ处理
     *
     * @param record         原始Stream记录
     * @param domain         任务领域
     * @param taskJson       任务载荷JSON
     * @param retryCount     当前重试次数
     * @param createdAtEpoch 首次入队时间戳(秒)
     * @param bizId          业务标识
     * @param traceId        链路追踪标识
     * @param exception      处理异常
     */
    private void retryOrDlq(MapRecord<String, Object, Object> record, String domain, String taskJson, int retryCount, String createdAtEpoch, String bizId, String traceId, Exception exception) {
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

            asyncTaskAuditService.record("DLQ_PUT", parseDomain(domain), Map.of(
                    "biz_id", bizId,
                    "trace_id", traceId,
                    "error_code", errorCode,
                    "retry_count", nextRetry
            ));
            return;
        }

        long backoffMs = computeBackoffMs(nextRetry);
        sleepQuietly(backoffMs);

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
        acknowledge(record);

        asyncTaskAuditService.record("RETRY_REQUEUE", parseDomain(domain), Map.of(
                "biz_id", bizId,
                "trace_id", traceId,
                "error_code", errorCode,
                "retry_count", nextRetry,
                "backoff_ms", backoffMs
        ));
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        stringRedisTemplate.opsForStream().acknowledge(AI_ASYNC_TASK_STREAM_KEY, streamGroup, record.getId());
    }

    /**
     * 确保主队列消费组存在。
     */
    private void ensureStreamGroup() {
        try {
            if (!stringRedisTemplate.hasKey(AI_ASYNC_TASK_STREAM_KEY)) {
                Map<String, String> init = new HashMap<>();
                init.put("bootstrap", "1");
                stringRedisTemplate.opsForStream().add(StreamRecords.string(init).withStreamKey(AI_ASYNC_TASK_STREAM_KEY));
            }
            stringRedisTemplate.opsForStream().createGroup(AI_ASYNC_TASK_STREAM_KEY, ReadOffset.latest(), streamGroup);
        } catch (Exception e) {
            if (isBusyGroupError(e)) {
                log.debug("统一异步 Streams 消费组已存在: {}", streamGroup);
            } else {
                log.warn("初始化统一异步 Streams 消费组时发生异常，group={}", streamGroup, e);
            }
        }
    }

    /**
     * 确保DLQ消费组存在。
     */
    private void ensureDlqStreamGroup() {
        try {
            if (!stringRedisTemplate.hasKey(AI_ASYNC_TASK_DLQ_STREAM_KEY)) {
                Map<String, String> init = new HashMap<>();
                init.put("bootstrap", "1");
                stringRedisTemplate.opsForStream().add(StreamRecords.string(init).withStreamKey(AI_ASYNC_TASK_DLQ_STREAM_KEY));
            }
            stringRedisTemplate.opsForStream().createGroup(AI_ASYNC_TASK_DLQ_STREAM_KEY, ReadOffset.latest(), AI_ASYNC_TASK_DLQ_STREAM_GROUP);
        } catch (Exception e) {
            if (isBusyGroupError(e)) {
                log.debug("统一异步 DLQ 消费组已存在: {}", AI_ASYNC_TASK_DLQ_STREAM_GROUP);
            } else {
                log.warn("初始化统一异步 DLQ 消费组时发生异常，group={}", AI_ASYNC_TASK_DLQ_STREAM_GROUP, e);
            }
        }
    }

    private void consumeDlqLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(AI_ASYNC_TASK_DLQ_STREAM_GROUP, consumerName + "-dlq"),
                        StreamReadOptions.empty().count(Math.max(batchSize, 1)).block(Duration.ofMillis(Math.max(blockMs, 1000))),
                        StreamOffset.create(AI_ASYNC_TASK_DLQ_STREAM_KEY, ReadOffset.lastConsumed())
                );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    disposeDlqRecord(record);
                }
            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted() || isRedisStoppedException(e)) {
                    log.debug("统一异步 DLQ 处置线程停止中，结束消费循环");
                    break;
                }
                log.error("处理统一异步 DLQ 时发生异常", e);
            }
        }
    }

    /**
     * 处理单条DLQ消息并按领域执行处置。
     *
     * @param record DLQ Stream记录
     */
    private void disposeDlqRecord(MapRecord<String, Object, Object> record) {
        String domain = asString(record.getValue().get("domain"));
        String taskJson = asString(record.getValue().get("task"));
        String bizId = asString(record.getValue().get("biz_id"));
        String traceId = asString(record.getValue().get("trace_id"));
        String errorCode = asString(record.getValue().get("error_code"));
        String lastError = asString(record.getValue().get("last_error"));

        try {
            AsyncTaskDomain asyncTaskDomain = parseDomain(domain);
            if (asyncTaskDomain == AsyncTaskDomain.NOTIFICATION_TASK) {
                replayDlqNotification(record, domain, taskJson, bizId, traceId, errorCode, lastError);
            } else if (asyncTaskDomain == AsyncTaskDomain.PERSONA_TASK) {
                // PERSONA_TASK 兼容两类载荷：AiAsyncTaskDTO（画像分析 ai_task）或 AiPersonaTaskDTO（其他画像任务）
                if (isAiTaskPayload(taskJson)) {
                    failVisibleDlqAiTask(taskJson, bizId, traceId, errorCode, lastError);
                } else {
                    compensateDropDlqPersona(taskJson, bizId, traceId, errorCode, lastError);
                }
                acknowledgeDlq(record);
            } else {
                asyncTaskAuditService.record("DLQ_UNKNOWN_DOMAIN", AsyncTaskDomain.NOTIFICATION_TASK, Map.of(
                        "biz_id", firstNonBlank(bizId, "unknown"),
                        "trace_id", firstNonBlank(traceId, "unknown"),
                        "domain", firstNonBlank(domain, "unknown")
                ));
                acknowledgeDlq(record);
            }
        } catch (Exception e) {
            log.error("DLQ 处置失败，recordId={}", record.getId(), e);
        }
    }

    /**
     * 对通知任务执行DLQ回放。
     *
     * @param record    DLQ Stream记录
     * @param domain    任务领域
     * @param taskJson  任务载荷JSON
     * @param bizId     业务标识
     * @param traceId   链路追踪标识
     * @param errorCode 错误码
     * @param lastError 最后一次错误信息
     */
    private void replayDlqNotification(MapRecord<String, Object, Object> record, String domain, String taskJson, String bizId, String traceId, String errorCode, String lastError) {
        int dlqReplay = parseRetryCount(record.getValue().get("dlq_replay_count")) + 1;
        if (dlqReplay > Math.max(notificationMaxDlqReplay, 0)) {
            asyncTaskAuditService.record("DLQ_REPLAY_EXHAUSTED", AsyncTaskDomain.NOTIFICATION_TASK, Map.of(
                    "biz_id", firstNonBlank(bizId, "unknown"),
                    "trace_id", firstNonBlank(traceId, "unknown"),
                    "error_code", firstNonBlank(errorCode, "UNKNOWN"),
                    "last_error", firstNonBlank(lastError, "unknown")
            ));
            acknowledgeDlq(record);
            return;
        }

        Map<String, String> payload = new HashMap<>();
        payload.put("domain", domain);
        payload.put("task", taskJson);
        payload.put("biz_id", firstNonBlank(bizId, "notify:unknown"));
        payload.put("trace_id", firstNonBlank(traceId, UUID.randomUUID().toString()));
        payload.put("retry_count", "0");
        payload.put("created_at", String.valueOf(Instant.now().getEpochSecond()));
        payload.put("dlq_replay_count", String.valueOf(dlqReplay));
        stringRedisTemplate.opsForStream().add(StreamRecords.string(payload).withStreamKey(AI_ASYNC_TASK_STREAM_KEY));
        acknowledgeDlq(record);

        asyncTaskAuditService.record("DLQ_REPLAY", AsyncTaskDomain.NOTIFICATION_TASK, Map.of(
                "biz_id", firstNonBlank(bizId, "unknown"),
                "trace_id", firstNonBlank(traceId, "unknown"),
                "dlq_replay_count", dlqReplay
        ));
    }

    /**
     * 对画像任务执行补偿丢弃。
     *
     * @param taskJson  任务载荷JSON
     * @param bizId     业务标识
     * @param traceId   链路追踪标识
     * @param errorCode 错误码
     * @param lastError 最后一次错误信息
     */
    private void compensateDropDlqPersona(String taskJson, String bizId, String traceId, String errorCode, String lastError) {
        AiPersonaTaskDTO task = JsonUtil.fromJson(taskJson, AiPersonaTaskDTO.class);
        if (task == null || task.getUserId() == null || task.getTaskType() == null) {
            return;
        }

        aiConfigService.compensatePersonaTaskDrop(task.getUserId(), task.getTaskType().name(), lastError);
        asyncTaskAuditService.record("DLQ_COMPENSATE_DROP", AsyncTaskDomain.PERSONA_TASK, Map.of(
                "biz_id", firstNonBlank(bizId, "unknown"),
                "trace_id", firstNonBlank(traceId, "unknown"),
                "error_code", firstNonBlank(errorCode, "UNKNOWN"),
                "persona_task_type", task.getTaskType().name()
        ));
    }

    /**
     * 对AI任务执行显性失败落库。
     *
     * @param taskJson  任务载荷JSON
     * @param bizId     业务标识
     * @param traceId   链路追踪标识
     * @param errorCode 错误码
     * @param lastError 最后一次错误信息
     */
    private void failVisibleDlqAiTask(String taskJson, String bizId, String traceId, String errorCode, String lastError) {
        AiAsyncTaskDTO task = JsonUtil.fromJson(taskJson, AiAsyncTaskDTO.class);
        if (task == null || task.getUserId() == null || task.getAiTaskId() == null) {
            return;
        }
        aiTaskExecutionService.failQueuedAiTask(task.getUserId(), task.getAiTaskId(), firstNonBlank(errorCode, ErrorCodes.AI_TASK_DLQ), lastError);
        asyncTaskAuditService.record("DLQ_FAIL_VISIBLE", AsyncTaskDomain.PERSONA_TASK, Map.of(
                "biz_id", firstNonBlank(bizId, "unknown"),
                "trace_id", firstNonBlank(traceId, "unknown"),
                "error_code", firstNonBlank(errorCode, "UNKNOWN"),
                "ai_task_id", task.getAiTaskId()
        ));
    }

    private boolean isAiTaskPayload(String taskJson) {
        if (taskJson == null || taskJson.isBlank()) {
            return false;
        }
        AiAsyncTaskDTO task = JsonUtil.fromJson(taskJson, AiAsyncTaskDTO.class);
        return task != null && task.getUserId() != null && task.getAiTaskId() != null;
    }

    private void acknowledgeDlq(MapRecord<String, Object, Object> record) {
        stringRedisTemplate.opsForStream().acknowledge(AI_ASYNC_TASK_DLQ_STREAM_KEY, AI_ASYNC_TASK_DLQ_STREAM_GROUP, record.getId());
    }

    /**
     * 解析任务领域枚举。
     *
     * @param domain 领域字符串
     * @return 解析后的领域，非法时返回null
     */
    private AsyncTaskDomain parseDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        try {
            if ("AI_TASK".equalsIgnoreCase(domain)) {
                return AsyncTaskDomain.PERSONA_TASK;
            }
            return AsyncTaskDomain.valueOf(domain);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析异常对应错误码。
     *
     * @param e 异常
     * @return 错误码
     */
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

    /**
     * 判断异常是否允许重试。
     *
     * @param e         异常
     * @param errorCode 错误码
     * @return true表示可重试
     */
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

    /**
     * 从异常链中提取统一异常基类。
     *
     * @param throwable 原始异常
     * @return BaseException 或 null
     */
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

    /**
     * 计算指数退避时长。
     *
     * @param retryCount 重试次数
     * @return 退避毫秒数
     */
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

    /**
     * 睡眠指定毫秒数，忽略中断。
     *
     * @param millis 睡眠时长（毫秒）
     */
    private void sleepQuietly(long millis) {
        long safe = Math.max(0L, millis);
        if (safe == 0L) {
            return;
        }
        try {
            Thread.sleep(safe);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 清理并截断错误信息，避免存储过长内容。
     *
     * @param error 原始错误信息
     * @return 清理后的错误信息
     */
    private String sanitizeError(String error) {
        if (error == null || error.isBlank()) {
            return "unknown";
        }
        return error.length() > 512 ? error.substring(0, 512) : error;
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param first    首选值
     * @param fallback 兜底值
     * @return 结果字符串
     */
    private String firstNonBlank(String first, String fallback) {
        return (first == null || first.isBlank()) ? fallback : first;
    }

    private int parseRetryCount(Object value) {
        String s = asString(value);
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(s), 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 判断异常链是否为 Redis BUSYGROUP（消费组已存在）错误。
     *
     * @param throwable 异常
     * @return true 表示已存在消费组
     */
    private boolean isBusyGroupError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            String className = current.getClass().getName();
            if (className != null && className.contains("RedisBusyException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isRedisStoppedException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.contains("LettuceConnectionFactory has been STOPPED")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void joinQuietly(Thread thread, long timeoutMs) {
        try {
            thread.join(Math.max(1L, timeoutMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
