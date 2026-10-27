package cn.redture.aiEngine.consumer;

import static cn.redture.common.constants.RedisConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.redture.aiEngine.handler.AiNotificationTaskHandler;
import cn.redture.aiEngine.handler.AiTaskDispatcher;
import cn.redture.common.integration.notification.DispatchResult;
import cn.redture.common.integration.notification.NotificationExternalService;

import org.junit.jupiter.api.*;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in: reads project YAML, but touches only random test-prefixed keys and groups. */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "sse.redis.it", matches = "true")
class AiNotificationRedisIT {
    private StringRedisTemplate redis;
    private LettuceConnectionFactory factory;
    private AiAsyncTaskErrorHandler errors;
    private String group;
    private String dlqGroup;
    private String consumer;
    private static final String TASK =
            "{\"aiTaskId\":1,\"taskPublicId\":\"it-job\",\"status\":\"COMPLETED\"}";

    @BeforeEach
    void connectIsolatedNamespace() throws Exception {
        Path root = Path.of(System.getProperty("sse.project.root", ".")).toAbsolutePath();
        StandardEnvironment env = new StandardEnvironment();
        for (String file :
                List.of(
                        "ai-social-app/src/main/resources/application.yaml",
                        "config/application-local.yaml")) {
            Path path = root.resolve(file);
            if (Files.exists(path)) {
                for (var source :
                        new YamlPropertySourceLoader().load(file, new FileSystemResource(path))) {
                    env.getPropertySources().addFirst(source);
                }
            }
        }
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(
                        env.getRequiredProperty("spring.data.redis.host"),
                        env.getProperty("spring.data.redis.port", Integer.class, 6379));
        config.setDatabase(env.getProperty("spring.data.redis.database", Integer.class, 0));
        String username = env.getProperty("spring.data.redis.username");
        if (username != null && !username.isBlank()) config.setUsername(username);
        String password = env.getProperty("spring.data.redis.password");
        if (password != null && !password.isBlank()) config.setPassword(RedisPassword.of(password));
        var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(5));
        if (env.getProperty("spring.data.redis.ssl.enabled", Boolean.class, false)) client.useSsl();
        factory = new LettuceConnectionFactory(config, client.build());
        factory.afterPropertiesSet();
        String prefix = "sse-it:" + UUID.randomUUID() + ":";
        redis = new StringRedisTemplate(factory);
        redis.setKeySerializer(
                new StringRedisSerializer() {
                    @Override
                    public byte[] serialize(String value) {
                        return super.serialize(prefix + value);
                    }

                    @Override
                    public String deserialize(byte[] bytes) {
                        String value = super.deserialize(bytes);
                        return value != null && value.startsWith(prefix)
                                ? value.substring(prefix.length())
                                : value;
                    }
                });
        redis.afterPropertiesSet();
        group = "group-" + UUID.randomUUID();
        dlqGroup = "dlq-" + UUID.randomUUID();
        consumer = "consumer-" + UUID.randomUUID();
        redis.opsForStream().add(AI_ASYNC_TASK_STREAM_KEY, Map.of("bootstrap", "1"));
        redis.opsForStream().createGroup(AI_ASYNC_TASK_STREAM_KEY, ReadOffset.latest(), group);
        redis.opsForStream().add(AI_ASYNC_TASK_DLQ_STREAM_KEY, Map.of("bootstrap", "1"));
        redis.opsForStream()
                .createGroup(AI_ASYNC_TASK_DLQ_STREAM_KEY, ReadOffset.latest(), dlqGroup);
        errors = new AiAsyncTaskErrorHandler();
        ReflectionTestUtils.setField(errors, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(errors, "streamGroup", group);
        ReflectionTestUtils.setField(errors, "maxRetries", 1);
        ReflectionTestUtils.setField(errors, "retryBaseMs", 100L);
        ReflectionTestUtils.setField(errors, "retryMaxMs", 100L);
    }

    @AfterEach
    void deleteOnlyOwnedKeysAndClose() {
        if (errors != null) errors.stopRetryPromotion();
        try {
            if (redis != null) {
                redis.delete(AI_ASYNC_TASK_STREAM_KEY);
                redis.delete(AI_ASYNC_TASK_DLQ_STREAM_KEY);
                redis.delete(AiAsyncTaskErrorHandler.RETRY_KEY);
            }
        } finally {
            if (factory != null) factory.destroy();
        }
    }

    @Test
    void normalOfflineFullPartialAndClosedHintsAreAcknowledgedAsDisposition() {
        for (DispatchResult result :
                List.of(
                        new DispatchResult(1, 1, 0, 0),
                        DispatchResult.noActiveConnection(),
                        new DispatchResult(2, 0, 2, 0),
                        new DispatchResult(2, 1, 1, 0),
                        new DispatchResult(1, 0, 0, 1))) {
            AiAsyncTaskStreamConsumer handler = consumer((u, t, p) -> result);
            ReflectionTestUtils.invokeMethod(handler, "processRecord", addAndRead());
            assertEquals(0, pending(AI_ASYNC_TASK_STREAM_KEY, group));
            assertEquals(0L, redis.opsForZSet().zCard(AiAsyncTaskErrorHandler.RETRY_KEY));
        }
    }

    @Test
    void retrySurvivesHandlerReplacementAndPreservesNotificationEnvelope() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AiAsyncTaskStreamConsumer handler =
                consumer(
                        (u, t, p) -> {
                            assertEquals(1L, u);
                            assertEquals("AI_TASK_COMPLETED", t);
                            if (attempts.incrementAndGet() == 1)
                                throw new RuntimeException("temporary bridge failure");
                            return DispatchResult.noActiveConnection();
                        });
        ReflectionTestUtils.invokeMethod(handler, "processRecord", addAndRead());
        assertEquals(0, pending(AI_ASYNC_TASK_STREAM_KEY, group));
        assertEquals(1L, redis.opsForZSet().zCard(AiAsyncTaskErrorHandler.RETRY_KEY));
        errors.stopRetryPromotion();
        AiAsyncTaskErrorHandler restarted = new AiAsyncTaskErrorHandler();
        ReflectionTestUtils.setField(restarted, "stringRedisTemplate", redis);
        try {
            Thread.sleep(150);
            restarted.promoteDueRetries();
            MapRecord<String, Object, Object> retry = read(AI_ASYNC_TASK_STREAM_KEY, group);
            assertEnvelope(retry);
            assertEquals("1", retry.getValue().get("retry_count"));
            ReflectionTestUtils.invokeMethod(handler, "processRecord", retry);
            assertEquals(2, attempts.get());
            assertEquals(0, pending(AI_ASYNC_TASK_STREAM_KEY, group));
        } finally {
            restarted.stopRetryPromotion();
        }
    }

    @Test
    void exhaustedFailureMovesToDlqAndReplayKeepsEnvelopeAndReplayLimit() {
        ReflectionTestUtils.setField(errors, "maxRetries", 0);
        AiAsyncTaskStreamConsumer handler =
                consumer(
                        (u, t, p) -> {
                            throw new RuntimeException("broken bridge");
                        });
        ReflectionTestUtils.invokeMethod(handler, "processRecord", addAndRead());
        assertEquals(0, pending(AI_ASYNC_TASK_STREAM_KEY, group));
        MapRecord<String, Object, Object> dlq = read(AI_ASYNC_TASK_DLQ_STREAM_KEY, dlqGroup);
        assertEnvelope(dlq);
        AiAsyncTaskDlqProcessor processor = new AiAsyncTaskDlqProcessor();
        ReflectionTestUtils.setField(processor, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(processor, "dlqGroup", dlqGroup);
        ReflectionTestUtils.setField(processor, "notificationMaxDlqReplay", 1);
        ReflectionTestUtils.invokeMethod(processor, "disposeDlqRecord", dlq);
        assertEquals(0, pending(AI_ASYNC_TASK_DLQ_STREAM_KEY, dlqGroup));
        MapRecord<String, Object, Object> replay = read(AI_ASYNC_TASK_STREAM_KEY, group);
        assertEnvelope(replay);
        assertEquals("1", replay.getValue().get("dlq_replay_count"));
        ReflectionTestUtils.invokeMethod(handler, "processRecord", replay);
        MapRecord<String, Object, Object> secondDlq = read(AI_ASYNC_TASK_DLQ_STREAM_KEY, dlqGroup);
        assertEquals("1", secondDlq.getValue().get("dlq_replay_count"));
        ReflectionTestUtils.invokeMethod(processor, "disposeDlqRecord", secondDlq);
        assertEquals(0, pending(AI_ASYNC_TASK_DLQ_STREAM_KEY, dlqGroup));
        assertTrue(
                redis.opsForStream()
                        .read(
                                Consumer.from(group, consumer),
                                StreamReadOptions.empty().count(1),
                                StreamOffset.create(
                                        AI_ASYNC_TASK_STREAM_KEY, ReadOffset.lastConsumed()))
                        .isEmpty());
    }

    @Test
    void failedRetryPersistenceLeavesSourcePendingForRecovery() {
        redis.opsForValue().set(AiAsyncTaskErrorHandler.RETRY_KEY, "wrong-type-fault");
        AiAsyncTaskStreamConsumer handler =
                consumer(
                        (u, t, p) -> {
                            throw new RuntimeException("temporary");
                        });
        MapRecord<String, Object, Object> source = addAndRead();
        assertThrows(
                RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(handler, "processRecord", source));
        assertEquals(1, pending(AI_ASYNC_TASK_STREAM_KEY, group));
        redis.delete(AiAsyncTaskErrorHandler.RETRY_KEY);
        ReflectionTestUtils.invokeMethod(handler, "processRecord", source);
        assertEquals(0, pending(AI_ASYNC_TASK_STREAM_KEY, group));
        assertEquals(1L, redis.opsForZSet().zCard(AiAsyncTaskErrorHandler.RETRY_KEY));
    }

    private AiAsyncTaskStreamConsumer consumer(NotificationExternalService bridge) {
        AiAsyncTaskStreamConsumer result = new AiAsyncTaskStreamConsumer();
        ReflectionTestUtils.setField(result, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(result, "streamGroup", group);
        ReflectionTestUtils.setField(result, "errorHandler", errors);
        ReflectionTestUtils.setField(
                result,
                "aiTaskDispatcher",
                new AiTaskDispatcher(List.of(new AiNotificationTaskHandler(bridge))));
        return result;
    }

    private MapRecord<String, Object, Object> addAndRead() {
        redis.opsForStream()
                .add(
                        AI_ASYNC_TASK_STREAM_KEY,
                        Map.of(
                                "domain",
                                "NOTIFICATION_TASK",
                                "task",
                                TASK,
                                "user_id",
                                "1",
                                "event_type",
                                "AI_TASK_COMPLETED",
                                "created_at_epoch",
                                "123",
                                "biz_id",
                                "it-job",
                                "trace_id",
                                "it-trace"));
        return read(AI_ASYNC_TASK_STREAM_KEY, group);
    }

    private MapRecord<String, Object, Object> read(String key, String readGroup) {
        var records =
                redis.opsForStream()
                        .read(
                                Consumer.from(readGroup, consumer),
                                StreamReadOptions.empty().count(1),
                                StreamOffset.create(key, ReadOffset.lastConsumed()));
        assertNotNull(records);
        assertEquals(1, records.size());
        return records.getFirst();
    }

    private long pending(String key, String readGroup) {
        return redis.opsForStream().pending(key, readGroup).getTotalPendingMessages();
    }

    private void assertEnvelope(MapRecord<String, Object, Object> record) {
        assertEquals("1", record.getValue().get("user_id"));
        assertEquals("AI_TASK_COMPLETED", record.getValue().get("event_type"));
        assertEquals("123", record.getValue().get("created_at_epoch"));
        assertEquals("it-job", record.getValue().get("biz_id"));
        assertEquals("it-trace", record.getValue().get("trace_id"));
    }
}
