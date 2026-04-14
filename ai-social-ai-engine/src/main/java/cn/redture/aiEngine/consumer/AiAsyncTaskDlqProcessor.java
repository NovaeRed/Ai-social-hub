package cn.redture.aiEngine.consumer;

import cn.redture.aiEngine.pojo.dto.AiAsyncTaskDTO;
import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import cn.redture.aiEngine.service.AiConfigService;
import cn.redture.aiEngine.service.AiTaskExecutionService;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.util.JsonUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_DLQ_STREAM_GROUP;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_DLQ_STREAM_KEY;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_KEY;

/**
 * 统一死信队列（DLQ）消费者处理器。
 */
@Slf4j
@Component
public class AiAsyncTaskDlqProcessor implements SmartLifecycle {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiTaskExecutionService aiTaskExecutionService;

    @Resource
    private AiConfigService aiConfigService;

    private Thread dlqThread;
    private volatile boolean running;

    @Value("${ai.async.dlq.consumer.enabled:true}")
    private boolean dlqConsumerEnabled;

    @Value("${spring.application.name:ai-social-app}")
    private String consumerName;

    @Value("${ai.async.queue.batch-size:10}")
    private int batchSize;

    @Value("${ai.async.queue.block-ms:5000}")
    private long blockMs;

    @Value("${ai.async.dlq.notification-max-replay:3}")
    private int notificationMaxDlqReplay;

    @Override
    public void start() {
        if (running) {
            return;
        }
        if (!dlqConsumerEnabled) {
            log.debug("统一异步 DLQ 消费者已禁用");
            return;
        }
        running = true;

        ensureDlqStreamGroup();

        dlqThread = new Thread(this::consumeDlqLoop, "ai-async-dlq-processor");
        dlqThread.setDaemon(true);
        dlqThread.start();
        log.info("统一异步 DLQ 处置线程已启动，group={}", AI_ASYNC_TASK_DLQ_STREAM_GROUP);
    }

    @Override
    public void stop() {
        running = false;
        if (dlqThread != null && dlqThread.isAlive()) {
            dlqThread.interrupt();
            try {
                dlqThread.join(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
        return Integer.MAX_VALUE - 101; // Ensure lower phase stays near streaming infra
    }

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

    private void disposeDlqRecord(MapRecord<String, Object, Object> record) {
        String domain = asString(record.getValue().get("domain"));
        String taskJson = asString(record.getValue().get("task"));
        String bizId = asString(record.getValue().get("biz_id"));
        String traceId = asString(record.getValue().get("trace_id"));
        String errorCode = asString(record.getValue().get("error_code"));
        String lastError = asString(record.getValue().get("last_error"));

        try {
            AsyncTaskDomain asyncTaskDomain = parseDomain(domain);

            // NOTIFICATION_TASK：进行重试，次数不大于 notificationMaxDlqReplay
            // PERSONA_TASK：如果是 AI 画像任务，直接标记失败；否则调用 compensate 接口进行补偿后 ACK
            if (asyncTaskDomain == AsyncTaskDomain.NOTIFICATION_TASK) {
                replayDlqNotification(record, domain, taskJson, bizId, traceId, errorCode, lastError);
            } else if (asyncTaskDomain == AsyncTaskDomain.PERSONA_TASK) {
                if (isAiTaskPayload(taskJson)) {
                    failVisibleDlqAiTask(taskJson, bizId, traceId, errorCode, lastError);
                } else {
                    compensateDropDlqPersona(taskJson, bizId, traceId, errorCode, lastError);
                }
                acknowledgeDlq(record);
            } else {
                acknowledgeDlq(record);
            }
        } catch (Exception e) {
            log.error("DLQ 处置失败，recordId={}", record.getId(), e);
        }
    }

    private void replayDlqNotification(MapRecord<String, Object, Object> record, String domain, String taskJson, String bizId, String traceId, String errorCode, String lastError) {
        int dlqReplay = parseRetryCount(record.getValue().get("dlq_replay_count")) + 1;
        if (dlqReplay > Math.max(notificationMaxDlqReplay, 0)) {
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
    }

    private void compensateDropDlqPersona(String taskJson, String bizId, String traceId, String errorCode, String lastError) {
        AiPersonaTaskDTO task = JsonUtil.fromJson(taskJson, AiPersonaTaskDTO.class);
        if (task == null || task.getUserId() == null || task.getTaskType() == null) {
            return;
        }

        aiConfigService.compensatePersonaTaskDrop(task.getUserId(), task.getTaskType().name(), lastError);
    }

    private void failVisibleDlqAiTask(String taskJson, String bizId, String traceId, String errorCode, String lastError) {
        AiAsyncTaskDTO task = JsonUtil.fromJson(taskJson, AiAsyncTaskDTO.class);
        if (task == null || task.getUserId() == null || task.getAiTaskId() == null) {
            return;
        }
        aiTaskExecutionService.failQueuedAiTask(task.getUserId(), task.getAiTaskId(), firstNonBlank(errorCode, ErrorCodes.AI_TASK_DLQ), lastError);
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

    private AsyncTaskDomain parseDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        try {
            return AsyncTaskDomain.valueOf(domain);
        } catch (Exception e) {
            return null;
        }
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

    private String firstNonBlank(String first, String fallback) {
        return (first == null || first.isBlank()) ? fallback : first;
    }

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
}