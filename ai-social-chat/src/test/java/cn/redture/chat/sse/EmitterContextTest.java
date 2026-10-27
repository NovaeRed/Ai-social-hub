package cn.redture.chat.sse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class EmitterContextTest {
    private final PushConfig config = new PushConfig();

    private EmitterContext context(int capacity) {
        return new EmitterContext(1L, "test", new SseEmitter(), capacity);
    }

    private QueuedEvent event(EventPriority priority) {
        return new QueuedEvent(
                new Notification<>("TEST", "data"), priority, System.currentTimeMillis(), "test");
    }

    @Test
    void closingWakesBlockedProducerAndRejectsLateEvent() throws Exception {
        EmitterContext context = context(1);
        config.getPriorityStrategy().setCriticalBlockTimeoutMs(5_000);
        assertTrue(context.enqueue(event(EventPriority.CRITICAL), config));
        AtomicReference<Boolean> accepted = new AtomicReference<>();
        Thread producer =
                Thread.ofVirtual()
                        .start(
                                () ->
                                        accepted.set(
                                                context.enqueue(
                                                        event(EventPriority.CRITICAL), config)));
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (producer.getState() != Thread.State.TIMED_WAITING
                    && System.nanoTime() < deadline) Thread.yield();
            assertEquals(Thread.State.TIMED_WAITING, producer.getState());
            context.markClosed();
            producer.join(500);
            assertFalse(producer.isAlive(), "closing must wake capacity waiters");
            assertEquals(Boolean.FALSE, accepted.get());
            assertEquals(0, context.getQueueSize());
            assertNull(context.pollNext(0, TimeUnit.MILLISECONDS, config.getPriorityStrategy()));
        } finally {
            producer.interrupt();
            producer.join(1_000);
        }
    }

    @Test
    void drainingStopsAdmissionButRetainsAcceptedEvents() throws Exception {
        EmitterContext context = context(2);
        assertTrue(context.enqueue(event(EventPriority.NORMAL), config));
        context.markInactive();
        assertFalse(context.enqueue(event(EventPriority.NORMAL), config));
        assertNotNull(context.pollNext(0, TimeUnit.MILLISECONDS, config.getPriorityStrategy()));
        assertTrue(context.isQueueEmpty());
    }

    @Test
    void rejectedEventDoesNotRefreshActivity() {
        EmitterContext context = context(1);
        assertTrue(context.enqueue(event(EventPriority.NORMAL), config));
        context.getLastActivityTime().set(123);
        assertFalse(context.enqueue(event(EventPriority.LOW), config));
        assertEquals(123, context.getLastActivityTime().get());
    }

    @Test
    void allPrioritiesShareCapacityAndPollingRestoresIt() throws Exception {
        EmitterContext context = context(3);
        assertTrue(context.enqueue(event(EventPriority.CRITICAL), config));
        assertTrue(context.enqueue(event(EventPriority.NORMAL), config));
        assertTrue(context.enqueue(event(EventPriority.LOW), config));
        assertFalse(context.enqueue(event(EventPriority.NORMAL), config));
        for (int i = 0; i < 3; i++)
            assertNotNull(context.pollNext(0, TimeUnit.MILLISECONDS, config.getPriorityStrategy()));
        assertEquals(0, context.getQueueSize());
        assertTrue(context.enqueue(event(EventPriority.NORMAL), config));
    }

    @Test
    void fixedBacklogGivesLowPriorityTheTwentyFourthSlot() throws Exception {
        EmitterContext context = context(100);
        for (int i = 0; i < 30; i++)
            assertTrue(context.enqueue(event(EventPriority.CRITICAL), config));
        for (int i = 0; i < 10; i++)
            assertTrue(context.enqueue(event(EventPriority.NORMAL), config));
        assertTrue(context.enqueue(event(EventPriority.LOW), config));
        QueuedEvent next = null;
        for (int i = 0; i < 24; i++)
            next = context.pollNext(0, TimeUnit.MILLISECONDS, config.getPriorityStrategy());
        assertNotNull(next);
        assertEquals(EventPriority.LOW, next.getPriority());
    }

    @Test
    void eventTypeContractUsesExactNames() {
        assertEquals(EventPriority.LOW, EventPriority.forType("MESSAGE_READ"));
        assertEquals(EventPriority.CRITICAL, EventPriority.forType("MESSAGE_CREATED"));
        assertEquals(EventPriority.NORMAL, EventPriority.forType("UNRECOGNIZED_MESSAGE_EXTENSION"));
        assertEquals(EventPriority.NORMAL, EventPriority.forType(null));
    }

    @Test
    void snapshotsPayloadAndDefendsDiagnosticViewFromMutation() {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("content", "original");
        Notification<?> notification = new Notification<>("MESSAGE_CREATED", payload);
        QueuedEvent event = new QueuedEvent(notification, EventPriority.CRITICAL, 0, "test");
        payload.put("content", "changed");
        notification.setType("MODIFIED");
        event.getNotification().setType("ALSO_MODIFIED");
        assertTrue(event.getSerializedNotification().contains("original"));
        assertFalse(event.getSerializedNotification().contains("changed"));
        assertEquals("MESSAGE_CREATED", event.getNotification().getType());
        assertEquals(
                event.getSerializedNotification()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        .length,
                event.getSizeBytes());
    }

    @Test
    void byteBudgetIsSharedAcrossConnectionsAndReleasedOnCloseAndPoll() throws Exception {
        QueuedEvent event = event(EventPriority.NORMAL);
        InstanceQueueBudget shared = new InstanceQueueBudget(event.getSizeBytes());
        EmitterContext first = new EmitterContext(1L, "first", new SseEmitter(), 3, shared);
        EmitterContext second = new EmitterContext(1L, "second", new SseEmitter(), 3, shared);
        assertTrue(first.enqueue(event, config));
        assertEquals(EmitterContext.EnqueueResult.FULL, second.enqueueResult(event, config, 0));
        first.markClosed();
        assertEquals(1, first.getAbandonedCount());
        assertEquals(0, first.getQueueBytes());
        assertEquals(0, shared.usedBytes());
        assertTrue(second.enqueue(event, config));
        assertNotNull(second.pollNext(0, TimeUnit.NANOSECONDS, config.getPriorityStrategy()));
        assertEquals(0, shared.usedBytes());
        second.markClosed();
        second.markClosed();
        assertEquals(0, shared.usedBytes());
    }

    @Test
    void perEventAndLocalBytesRejectWithoutReserving() throws Exception {
        EmitterContext context = context(3);
        QueuedEvent event = event(EventPriority.NORMAL);
        config.setMaxEventBytes(event.getSizeBytes() - 1);
        assertEquals(
                EmitterContext.EnqueueResult.TOO_LARGE, context.enqueueResult(event, config, 0));
        assertEquals(0, context.getQueueBytes());
        config.setMaxEventBytes(event.getSizeBytes());
        config.setMaxQueueBytes(event.getSizeBytes());
        assertTrue(context.enqueue(event, config));
        assertEquals(EmitterContext.EnqueueResult.FULL, context.enqueueResult(event, config, 0));
        assertEquals(event.getSizeBytes(), context.getQueueBytes());
        context.pollNext(0, TimeUnit.NANOSECONDS, config.getPriorityStrategy());
        assertEquals(0, context.getQueueBytes());
    }

    @Test
    void callerBudgetBoundsCriticalWait() {
        EmitterContext context = context(1);
        config.getPriorityStrategy().setCriticalBlockTimeoutMs(5_000);
        assertTrue(context.enqueue(event(EventPriority.CRITICAL), config));
        long started = System.nanoTime();
        assertEquals(
                EmitterContext.EnqueueResult.FULL,
                context.enqueueResult(
                        event(EventPriority.CRITICAL), config, TimeUnit.MILLISECONDS.toNanos(20)));
        long elapsed = System.nanoTime() - started;
        assertTrue(
                elapsed < TimeUnit.MILLISECONDS.toNanos(500),
                "caller budget must override per-connection timeout");
    }

    @Test
    void queueAgeStartsAtLocalAcceptanceNotNotificationCreation() throws Exception {
        EmitterContext context = context(2);
        QueuedEvent event = event(EventPriority.NORMAL);
        long beforeAcceptance = System.nanoTime();
        while (System.nanoTime() - beforeAcceptance < TimeUnit.MILLISECONDS.toNanos(2))
            Thread.onSpinWait();
        long acceptedAfter = System.nanoTime();
        assertTrue(context.enqueue(event, config));
        QueuedEvent accepted =
                context.pollNext(0, TimeUnit.NANOSECONDS, config.getPriorityStrategy());
        assertNotNull(accepted);
        assertTrue(accepted.getEnqueuedAtNanos() >= acceptedAfter);
        assertEquals(0, context.getOldestQueuedAgeNanos(System.nanoTime()));
    }

    @Test
    void defaultWireContractUsesIsoDatesAndOmitsNulls() {
        QueuedEvent date =
                new QueuedEvent(
                        new Notification<>(
                                "DATE",
                                java.time.OffsetDateTime.parse("2026-10-27T10:15:30+08:00")),
                        EventPriority.NORMAL,
                        0,
                        "test");
        assertTrue(date.getSerializedNotification().contains("2026-10-27T10:15:30+08:00"));
        QueuedEvent noPayload =
                new QueuedEvent(new Notification<>("NONE", null), EventPriority.NORMAL, 0, "test");
        assertFalse(noPayload.getSerializedNotification().contains("payload"));
    }

    @Test
    void suppliedApplicationMapperControlsWireSerialization() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                QueuedEvent.defaultMapper()
                        .setSerializationInclusion(
                                com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS);
        QueuedEvent event =
                new QueuedEvent(
                        new Notification<>("NONE", null), EventPriority.NORMAL, 0, "test", mapper);
        assertTrue(event.getSerializedNotification().contains("\"payload\":null"));
    }

    @Test
    void concurrentCloseLeavesNoRetainedEventsOrInstanceBytes() throws Exception {
        InstanceQueueBudget shared = new InstanceQueueBudget(10_000);
        EmitterContext context = new EmitterContext(1L, "stress", new SseEmitter(), 3, shared);
        java.util.concurrent.atomic.AtomicInteger accepted =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger polled =
                new java.util.concurrent.atomic.AtomicInteger();
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var consumer =
                    workers.submit(
                            () -> {
                                try {
                                    while (!context.isClosed()) {
                                        if (context.pollNext(
                                                        10,
                                                        TimeUnit.MILLISECONDS,
                                                        config.getPriorityStrategy())
                                                != null) polled.incrementAndGet();
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
            java.util.List<java.util.concurrent.Future<?>> producers = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++)
                producers.add(
                        workers.submit(
                                () -> {
                                    for (int j = 0; j < 100; j++)
                                        if (context.enqueue(event(EventPriority.CRITICAL), config))
                                            accepted.incrementAndGet();
                                }));
            for (var producer : producers) producer.get(5, TimeUnit.SECONDS);
            context.markClosed();
            consumer.get(2, TimeUnit.SECONDS);
        }
        assertEquals(accepted.get(), polled.get() + context.getAbandonedCount());
        assertEquals(0, shared.usedBytes());
        assertEquals(0, context.getQueueSize());
        assertEquals(0, context.getQueueBytes());
        assertFalse(
                context.transitionState(EmitterContext.State.CLOSED, EmitterContext.State.ACTIVE));
        assertEquals(
                EmitterContext.EnqueueResult.CLOSED,
                context.enqueueResult(event(EventPriority.CRITICAL), config, 0));
    }

    @Test
    void closeWakesEveryWaitingProducerAndReturnsAllInstanceBytes() throws Exception {
        config.getPriorityStrategy().setCriticalBlockTimeoutMs(5_000);
        QueuedEvent event = event(EventPriority.CRITICAL);
        var shared = new InstanceQueueBudget(event.getSizeBytes());
        var context = new EmitterContext(1L, "many-waiters", new SseEmitter(), 1, shared);
        assertTrue(context.enqueue(event, config));
        var results = new java.util.ArrayList<AtomicReference<EmitterContext.EnqueueResult>>();
        var producers = new java.util.ArrayList<Thread>();
        try {
            for (int i = 0; i < 6; i++) {
                var result = new AtomicReference<EmitterContext.EnqueueResult>();
                results.add(result);
                producers.add(
                        Thread.ofVirtual()
                                .start(
                                        () ->
                                                result.set(
                                                        context.enqueueResult(
                                                                event,
                                                                config,
                                                                TimeUnit.SECONDS.toNanos(5)))));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (producers.stream()
                            .anyMatch(thread -> thread.getState() != Thread.State.TIMED_WAITING)
                    && System.nanoTime() < deadline) Thread.yield();
            assertTrue(
                    producers.stream()
                            .allMatch(thread -> thread.getState() == Thread.State.TIMED_WAITING),
                    "every producer must be waiting before the close");
            context.markClosed();
            for (Thread producer : producers) {
                producer.join(500);
                assertFalse(producer.isAlive(), "close must wake every producer");
            }
            for (var result : results)
                assertEquals(EmitterContext.EnqueueResult.CLOSED, result.get());
            assertEquals(0, shared.usedBytes());
            assertEquals(0, context.getQueueBytes());
            assertEquals(0, context.getQueueSize());
            assertEquals(1, context.getAbandonedCount());
        } finally {
            context.markClosed();
            for (Thread producer : producers) producer.interrupt();
            for (Thread producer : producers) producer.join(1_000);
        }
    }
}
