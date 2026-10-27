package cn.redture.chat.sse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.*;

class ConnectionRegistryTest {
    @Test
    void delayedCleanupOfVictimCannotRemoveReplacement() {
        var registry = new ConnectionRegistry();
        var old = connection("old");
        var replacement = connection("new");
        assertNull(registry.register(old, 1));
        assertSame(old, registry.register(replacement, 1));
        registry.remove(old);
        registry.remove(old);
        assertEquals(java.util.List.of(replacement), registry.connections(1L));
    }

    @Test
    void cleanupAndCreationUseSameRegistryBoundary() throws Exception {
        var registry = new ConnectionRegistry();
        for (int i = 0; i < 100; i++) {
            var old = connection("old");
            var next = connection("next");
            registry.register(old, 1);
            var barrier = new CyclicBarrier(2);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var clean =
                        executor.submit(
                                () -> {
                                    barrier.await();
                                    registry.remove(old);
                                    return null;
                                });
                var create =
                        executor.submit(
                                () -> {
                                    barrier.await();
                                    registry.register(next, 1);
                                    return null;
                                });
                clean.get(2, TimeUnit.SECONDS);
                create.get(2, TimeUnit.SECONDS);
            }
            assertEquals(java.util.List.of(next), registry.connections(1L));
            registry.remove(next);
            assertEquals(0, registry.size());
        }
    }

    @Test
    void queueBacklogTieBreakEvictsLargerBacklog() {
        var registry = new ConnectionRegistry();
        var one = connection("one");
        var two = connection("two");
        var config = new PushConfig();
        var event =
                new QueuedEvent(
                        new Notification<>("MESSAGE_CREATED", "x"),
                        EventPriority.CRITICAL,
                        0,
                        "trace");
        one.context().enqueue(event, config);
        two.context().enqueue(event, config);
        two.context().enqueue(event, config);
        one.context().getLastActivityTime().set(1);
        two.context().getLastActivityTime().set(1);
        registry.register(one, 2);
        registry.register(two, 2);
        assertSame(two, registry.register(connection("third"), 2));
    }

    static SseConnection connection(String id) {
        return new SseConnection(new EmitterContext(1L, id, new SseEmitter(), 8));
    }
}
