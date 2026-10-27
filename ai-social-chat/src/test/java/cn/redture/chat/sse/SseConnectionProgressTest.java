package cn.redture.chat.sse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseConnectionProgressTest {
    @Test
    void queueOccupationDurationResetsAfterPressureClears() throws Exception {
        var context = new EmitterContext(1L, "pressure", new SseEmitter(), 1);
        var config = new PushConfig();
        var connection = new SseConnection(context);
        context.enqueue(
                new QueuedEvent(
                        new Notification<>("MESSAGE_CREATED", "x"),
                        EventPriority.CRITICAL,
                        0,
                        "trace"),
                config);
        assertTrue(connection.observeQueueOccupation(1000, config));
        assertEquals(500, connection.queueHighAgeNanos(1500));
        context.pollNext(
                0, java.util.concurrent.TimeUnit.NANOSECONDS, config.getPriorityStrategy());
        assertFalse(connection.observeQueueOccupation(2000, config));
        assertEquals(0, connection.queueHighAgeNanos(2500));
    }

    @Test
    void successTimestampStaysUnsetUntilAWriteActuallySucceeds() {
        var context = new EmitterContext(1L, "progress", new SseEmitter(), 1);
        var connection = new SseConnection(context);
        assertEquals(0, connection.lastSuccessfulWriteAt(), "creation is not a successful write");
        assertTrue(connection.writeStarted());
        assertEquals(0, connection.lastSuccessfulWriteAt(), "starting a write is not success");
        assertTrue(connection.writeAgeNanos(System.nanoTime()) >= 0);
        connection.writeFinished(false);
        assertEquals(
                0, connection.lastSuccessfulWriteAt(), "failed writes must not report success");
        context.incrementConsecutiveFailures();
        assertTrue(connection.writeStarted());
        long beforeSuccess = System.currentTimeMillis();
        connection.writeFinished(true);
        assertTrue(connection.lastSuccessfulWriteAt() >= beforeSuccess);
        assertEquals(0, context.getConsecutiveFailures().get());
        assertEquals(0, connection.writeAgeNanos(System.nanoTime()));
    }
}
