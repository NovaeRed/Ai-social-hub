package cn.redture.chat.sse;

import static org.junit.jupiter.api.Assertions.*;

import cn.redture.common.integration.notification.DispatchResult;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class SseWriterTest {
    @Test
    void rejectionWarningRequiresMinimumSampleAndRecoversWhenWindowExpires() throws Exception {
        var config = new PushConfig();
        config.setQueueCapacity(1);
        config.setDispatchTimeoutMs(0);
        config.setWarningWindowMs(240);
        config.setWarningMinSamples(4);
        config.setCriticalRejectionWarningRatio(.5);
        config.setWarningSustainMs(0);
        config.setInspectionIntervalMs(10_000);
        var blocked = new FailingEmitter();
        try (var service = new SseEmitterServiceImpl(config, () -> blocked)) {
            service.createEmitter(8L);
            assertTrue(blocked.entered.await(2, TimeUnit.SECONDS));
            service.sendToUser(8L, new Notification<>("MESSAGE_CREATED", "accepted"));
            service.sendToUser(8L, new Notification<>("MESSAGE_CREATED", "rejected"));
            service.sendToUser(8L, new Notification<>("MESSAGE_CREATED", "rejected"));
            service.inspect();
            assertEquals(0, count(service, "warning.raised.criticalRejectionRate"));
            service.sendToUser(8L, new Notification<>("MESSAGE_CREATED", "rejected"));
            service.inspect();
            assertEquals(1, count(service, "warning.raised.criticalRejectionRate"));
            Thread.sleep(260);
            service.inspect();
            assertEquals(1, count(service, "warning.recovered.criticalRejectionRate"));
            blocked.release.countDown();
        } finally {
            blocked.release.countDown();
        }
    }

    @Test
    void sparseApplicationFailuresAccumulateUntilSuccessfulWriteResetsThem() throws Exception {
        var attempts = new AtomicInteger();
        var emitter =
                new SseEmitter() {
                    @Override
                    public void send(SseEventBuilder builder) {
                        int attempt = attempts.incrementAndGet();
                        if (attempt != 1)
                            throw new IllegalArgumentException("recoverable event error");
                    }
                };
        var config = new PushConfig();
        config.setHeartbeatIntervalMs(25);
        config.setSlowCountResetIntervalMs(1);
        try (var service = new SseEmitterServiceImpl(config, () -> emitter)) {
            service.createEmitter(5L);
            await(() -> count(service, "connection.closed.APPLICATION_FAILURE") == 1);
            assertEquals(4, attempts.get());
            assertEquals(3, count(service, "write.failed.APPLICATION"));
        }
        var connection = new SseConnection(new EmitterContext(1L, "reset", new SseEmitter(), 1));
        connection.context().incrementConsecutiveFailures();
        connection.writeStarted();
        connection.writeFinished(true);
        assertEquals(0, connection.context().getConsecutiveFailures().get());
    }

    @Test
    void sustainedCapacityWarningRecoversAndMetricsCanBeDisabled() throws Exception {
        var config = new PushConfig();
        config.setMaxConnectionsPerInstance(1);
        config.setWarningSustainMs(0);
        try (var service = new SseEmitterServiceImpl(config)) {
            service.createEmitter(6L);
            service.inspect();
            assertEquals(1, count(service, "warning.raised.capacity"));
            service.close();
            await(() -> ((Number) service.diagnostics().get("liveResources")).intValue() == 0);
            service.inspect();
            assertEquals(1, count(service, "warning.recovered.capacity"));
        }
        config.setEnableMetrics(false);
        try (var service = new SseEmitterServiceImpl(config)) {
            service.sendToUser(1L, new Notification<>("MESSAGE_CREATED", "offline"));
            assertTrue(((java.util.Map<?, ?>) service.diagnostics().get("counters")).isEmpty());
        }
    }

    @Test
    void causeChainAndHeartbeatUseSameDisconnectClassification() {
        assertEquals(
                SendFailureClassifier.Failure.PEER_DISCONNECTED,
                SendFailureClassifier.classify(
                        new IllegalStateException("outer", new IOException("Broken pipe"))));
        assertEquals(
                SendFailureClassifier.Failure.WRITE_TIMEOUT,
                SendFailureClassifier.classify(
                        new IOException("outer", new java.net.SocketTimeoutException())));
        assertEquals(
                SendFailureClassifier.Failure.WRITE_TIMEOUT,
                SendFailureClassifier.classify(
                        new org.springframework.web.context.request.async
                                .AsyncRequestNotUsableException(
                                "ServletOutputStream failed",
                                new java.net.SocketTimeoutException("write"))));
    }

    @Test
    void brokenPipeStopsWithoutSendingNextEventAndCompletesExactlyOnce() throws Exception {
        var emitter = new FailingEmitter();
        var config = new PushConfig();
        try (var service = new SseEmitterServiceImpl(config, () -> emitter)) {
            service.createEmitter(1L);
            assertTrue(emitter.entered.await(2, TimeUnit.SECONDS));
            var context = service.connections(1L).getFirst();
            service.sendToUser(1L, new Notification<>("MESSAGE_CREATED", "next"));
            emitter.release.countDown();
            context.getWriterThread().join(2000);
            await(() -> emitter.completed.get() == 1);
            assertEquals(1, emitter.attempts.get());
            assertEquals(1, emitter.completed.get());
            assertEquals(0, emitter.failedCompletion.get());
            assertTrue(service.connections(1L).isEmpty());
            assertEquals(1, count(service, "write.failed.PEER_DISCONNECTED"));
            assertEquals(0, count(service, "write.succeeded"));
            assertEquals(1, count(service, "event.abandoned"));
        } finally {
            emitter.release.countDown();
        }
    }

    @Test
    void heartbeatDisconnectIsTerminalAndCounted() throws Exception {
        var emitter =
                new SseEmitter() {
                    int attempts;

                    @Override
                    public void send(SseEventBuilder builder) throws IOException {
                        if (++attempts > 1) throw new IOException("Broken pipe");
                    }
                };
        var config = new PushConfig();
        config.setHeartbeatIntervalMs(20);
        try (var service = new SseEmitterServiceImpl(config, () -> emitter)) {
            service.createEmitter(2L);
            await(() -> count(service, "write.failed.PEER_DISCONNECTED") == 1);
            assertTrue(service.connections(2L).isEmpty());
            assertEquals(1, count(service, "write.succeeded"));
        }
    }

    @Test
    void inspectionDetectsBlockedWriteIndependentlyAndDoesNotKillIdleConnection() throws Exception {
        var blocked = new FailingEmitter();
        var config = new PushConfig();
        config.setWriteStallTimeoutMs(70);
        config.setTransportWriteTimeoutMs(150);
        config.setInspectionIntervalMs(10);
        config.setWarningSustainMs(0);
        try (var service = new SseEmitterServiceImpl(config, () -> blocked)) {
            service.createEmitter(3L);
            assertTrue(blocked.entered.await(2, TimeUnit.SECONDS));
            await(() -> count(service, "connection.closed.WRITE_TIMEOUT") == 1);
            assertTrue(service.connections(3L).isEmpty());
            // Controlled emitter ignores interruption, so detection is not evidence of release.
            assertEquals(1, ((Number) service.diagnostics().get("liveResources")).intValue());
            blocked.release.countDown();
            await(() -> ((Number) service.diagnostics().get("liveResources")).intValue() == 0);
        } finally {
            blocked.release.countDown();
        }
        try (var service = new SseEmitterServiceImpl(config)) {
            service.createEmitter(4L);
            Thread.sleep(150);
            assertEquals(1, service.connections(4L).size());
        }
    }

    @Test
    void dispatchUsesOneTotalWaitBudgetAndReportsOfflineFullAndPartial() throws Exception {
        var config = new PushConfig();
        config.setQueueCapacity(1);
        config.setDispatchTimeoutMs(40);
        config.getPriorityStrategy().setCriticalBlockTimeoutMs(200);
        var emitters = new java.util.ArrayList<FailingEmitter>();
        try (var service =
                new SseEmitterServiceImpl(
                        config,
                        () -> {
                            var emitter = new FailingEmitter();
                            emitters.add(emitter);
                            return emitter;
                        })) {
            assertEquals(
                    DispatchResult.Status.NO_ACTIVE_CONNECTION,
                    service.sendToUser(1L, new Notification<>("MESSAGE_CREATED", "offline"))
                            .status());
            for (int i = 0; i < 5; i++) service.createEmitter(1L);
            for (var emitter : emitters) assertTrue(emitter.entered.await(2, TimeUnit.SECONDS));
            assertEquals(
                    5,
                    service.sendToUser(1L, new Notification<>("MESSAGE_CREATED", "fill"))
                            .enqueuedConnections());
            long start = System.nanoTime();
            var result = service.sendToUser(1L, new Notification<>("MESSAGE_CREATED", "full"));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertEquals(DispatchResult.Status.REJECTED_FULL, result.status());
            assertEquals(5, result.rejectedFull());
            assertTrue(
                    elapsedMs < 200,
                    "one total 40ms budget should not become five 200ms waits: " + elapsedMs);
            // Free exactly one connection queue without releasing its blocked transport.
            service.connections(1L)
                    .getFirst()
                    .pollNext(0, TimeUnit.MILLISECONDS, config.getPriorityStrategy());
            result = service.sendToUser(1L, new Notification<>("MESSAGE_CREATED", "partial"));
            assertEquals(DispatchResult.Status.PARTIALLY_ENQUEUED, result.status());
            assertEquals(1, result.enqueuedConnections());
            emitters.forEach(emitter -> emitter.release.countDown());
        } finally {
            emitters.forEach(emitter -> emitter.release.countDown());
        }
    }

    @Test
    void instanceBudgetCountsDetachedWritersUntilTheyExit() throws Exception {
        var config = new PushConfig();
        config.setMaxConnectionsPerUser(1);
        config.setMaxConnectionsPerInstance(2);
        var blocked = new FailingEmitter();
        var number = new AtomicInteger();
        try (var service =
                new SseEmitterServiceImpl(
                        config, () -> number.getAndIncrement() == 0 ? blocked : new SseEmitter())) {
            service.createEmitter(1L);
            assertTrue(blocked.entered.await(2, TimeUnit.SECONDS));
            service.createEmitter(1L);
            assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> service.createEmitter(2L));
            blocked.release.countDown();
            await(() -> ((Number) service.diagnostics().get("liveResources")).intValue() == 1);
            assertNotNull(service.createEmitter(2L));
        } finally {
            blocked.release.countDown();
        }
    }

    @SuppressWarnings("unchecked")
    static long count(SseEmitterServiceImpl service, String code) {
        return ((java.util.Map<String, Long>) service.diagnostics().get("counters"))
                .getOrDefault(code, 0L);
    }

    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "condition did not complete within 3 seconds");
    }

    static final class FailingEmitter extends SseEmitter {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger failedCompletion = new AtomicInteger();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            attempts.incrementAndGet();
            entered.countDown();
            boolean waiting = true;
            while (waiting)
                try {
                    release.await();
                    waiting = false;
                } catch (InterruptedException ignored) {
                }
            throw new IOException("Broken pipe");
        }

        @Override
        public void complete() {
            completed.incrementAndGet();
        }

        @Override
        public void completeWithError(Throwable error) {
            failedCompletion.incrementAndGet();
        }
    }
}
