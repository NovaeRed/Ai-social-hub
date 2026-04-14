package cn.redture.aiEngine.consumer;

import cn.redture.aiEngine.handler.AiTaskDispatcher;
import cn.redture.common.util.TraceContext;
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
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_GROUP;
import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_KEY;

/**
 * 统一异步任务 Streams 消费者 (经过重构后的精简核心层)。
 * 只负责接收数据、提取 MDC 数据，并委派给 AiTaskDispatcher 策略模式路由。
 */
@Slf4j
@Component
public class AiAsyncTaskStreamConsumer implements SmartLifecycle {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiTaskDispatcher aiTaskDispatcher;

    @Resource
    private AiAsyncTaskErrorHandler errorHandler;

    private Thread consumerThread;
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

    @Override
    public void start() {
        if (running) {
            return;
        }
        if (!consumerEnabled) {
            log.debug("统一异步 Streams 消费者已禁用");
            return;
        }
        running = true;

        ensureStreamGroup();

        consumerThread = new Thread(this::consumeLoop, "ai-async-stream-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("统一异步 Streams 消费者线程已启动，group={}, consumer={}", streamGroup, consumerName);
    }

    @Override
    public void stop() {
        running = false;
        if (consumerThread != null && consumerThread.isAlive()) {
            consumerThread.interrupt();
            try {
                consumerThread.join(2000L);
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
        return Integer.MAX_VALUE - 100;
    }

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
            aiTaskDispatcher.dispatch(domain, envelopeEventType, envelopeUserId, taskJson, record.getId().getValue());
            acknowledge(record);
        } catch (Exception e) {
            log.error("处理统一异步任务失败，recordId={}, retryCount={}", record.getId(), retryCount, e);
            errorHandler.handleFailure(record, domain, taskJson, retryCount, createdAt, bizId, traceId, e);
        } finally {
            TraceContext.clear();
        }
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        stringRedisTemplate.opsForStream().acknowledge(AI_ASYNC_TASK_STREAM_KEY, streamGroup, record.getId());
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
