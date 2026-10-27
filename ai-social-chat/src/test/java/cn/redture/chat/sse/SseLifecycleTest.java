package cn.redture.chat.sse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class SseLifecycleTest {
    @Test
    void replacingOnlyConnectionKeepsReplacementRegisteredAndStopsOldWriter() throws Exception {
        PushConfig config = new PushConfig();
        config.setMaxConnectionsPerUser(1);
        var service = new SseEmitterServiceImpl(config);
        try {
            var first = service.createEmitter(1L);
            EmitterContext old = service.connections(1L).getFirst();
            var second = service.createEmitter(1L);
            assertEquals(1, service.connections(1L).size(), "replacement must remain discoverable");
            var replacement = service.connections(1L).getFirst();
            assertSame(second, replacement.getEmitter());
            assertNotSame(first, second);
            old.getWriterThread().join(2000);
            assertFalse(old.getWriterThread().isAlive());
            assertTrue(replacement.isActive());
        } finally {
            service.close();
        }
    }

    @Test
    void concurrentAdmissionNeverLeavesMoreThanConfiguredConnections() throws Exception {
        var config = new PushConfig();
        config.setMaxConnectionsPerUser(3);
        var service = new SseEmitterServiceImpl(config);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks =
                    java.util.stream.IntStream.range(0, 32)
                            .mapToObj(
                                    i ->
                                            pool.submit(
                                                    () -> {
                                                        start.await();
                                                        return service.createEmitter(9L);
                                                    }))
                            .toList();
            start.countDown();
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
            assertEquals(3, service.connections(9L).size());
        } finally {
            service.close();
        }
    }

    @Test
    void shutdownRejectsCreationAlreadyPausedInEmitterFactoryAndReleasesItsSlot() throws Exception {
        var config = new PushConfig();
        config.setMaxConnectionsPerInstance(1);
        var factoryEntered = new CountDownLatch(1);
        var releaseFactory = new CountDownLatch(1);
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        var service =
                new SseEmitterServiceImpl(
                        config,
                        () -> {
                            factoryEntered.countDown();
                            try {
                                if (!releaseFactory.await(3, TimeUnit.SECONDS))
                                    throw new IllegalStateException("factory not released");
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(error);
                            }
                            return emitter;
                        });
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var creation = pool.submit(() -> service.createEmitter(7L));
            try {
                assertTrue(factoryEntered.await(2, TimeUnit.SECONDS));
                service.close();
                releaseFactory.countDown();
                var failure =
                        assertThrows(
                                java.util.concurrent.ExecutionException.class,
                                () -> creation.get(3, TimeUnit.SECONDS));
                assertInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class,
                        failure.getCause());
                var response =
                        (org.springframework.web.server.ResponseStatusException) failure.getCause();
                assertEquals(503, response.getStatusCode().value());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (((Number) service.diagnostics().get("liveResources")).intValue() != 0
                        && System.nanoTime() < deadline) Thread.yield();
                assertEquals(0, ((Number) service.diagnostics().get("liveResources")).intValue());
                assertEquals(
                        0,
                        ((Number) service.diagnostics().get("registeredConnections")).intValue());
                assertEquals(0, ((Number) service.diagnostics().get("queuedBytes")).intValue());
                var slots =
                        (java.util.concurrent.Semaphore)
                                org.springframework.test.util.ReflectionTestUtils.getField(
                                        service, "instanceSlots");
                assertNotNull(slots);
                assertEquals(
                        config.getMaxConnectionsPerInstance(),
                        slots.availablePermits(),
                        "failed admission must return its reserved slot");
            } finally {
                releaseFactory.countDown();
                service.close();
            }
        }
    }
}
