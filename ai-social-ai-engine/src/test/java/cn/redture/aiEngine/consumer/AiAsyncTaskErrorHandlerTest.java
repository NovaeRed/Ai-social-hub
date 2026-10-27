package cn.redture.aiEngine.consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

class AiAsyncTaskErrorHandlerTest {
    @Test
    void retryIsPersistedBeforeSourceIsAcknowledgedAndKeepsEnvelope() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        AiAsyncTaskErrorHandler handler = handler(redis);
        try {
            handler.handleFailure(
                    record(),
                    "NOTIFICATION_TASK",
                    "{}",
                    0,
                    "123",
                    "biz",
                    "trace",
                    new RuntimeException("temporary"));
            verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
            verify(stream, never())
                    .acknowledge(
                            anyString(),
                            anyString(),
                            any(org.springframework.data.redis.connection.stream.RecordId.class));
        } finally {
            stopScheduler(handler);
        }
    }

    @Test
    void failedRedisPersistenceDoesNotAcknowledgeSource() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis unavailable"));
        AiAsyncTaskErrorHandler handler = handler(redis);
        try {
            assertThrows(
                    RuntimeException.class,
                    () ->
                            handler.handleFailure(
                                    record(),
                                    "NOTIFICATION_TASK",
                                    "{}",
                                    0,
                                    "123",
                                    "biz",
                                    "trace",
                                    new RuntimeException("temporary")));
            verify(stream, never())
                    .acknowledge(
                            anyString(),
                            anyString(),
                            any(org.springframework.data.redis.connection.stream.RecordId.class));
        } finally {
            stopScheduler(handler);
        }
    }

    private AiAsyncTaskErrorHandler handler(StringRedisTemplate redis) {
        AiAsyncTaskErrorHandler handler = new AiAsyncTaskErrorHandler();
        ReflectionTestUtils.setField(handler, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(handler, "maxRetries", 2);
        ReflectionTestUtils.setField(handler, "streamGroup", "test-group");
        return handler;
    }

    private MapRecord<String, Object, Object> record() {
        return StreamRecords.newRecord()
                .in("source")
                .ofMap(
                        Map.<Object, Object>of(
                                "domain",
                                "NOTIFICATION_TASK",
                                "task",
                                "{}",
                                "user_id",
                                "1",
                                "event_type",
                                "AI_TASK_COMPLETED",
                                "dlq_replay_count",
                                "2",
                                "created_at_epoch",
                                "123"))
                .withId(org.springframework.data.redis.connection.stream.RecordId.of("1-0"));
    }

    private void stopScheduler(AiAsyncTaskErrorHandler handler) {
        ((ScheduledExecutorService) ReflectionTestUtils.getField(handler, "scheduler"))
                .shutdownNow();
    }
}
