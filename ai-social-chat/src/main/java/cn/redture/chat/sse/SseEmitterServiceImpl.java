package cn.redture.chat.sse;

import static cn.redture.chat.sse.SseConnection.CloseReason.*;

import cn.redture.common.integration.notification.DispatchResult;
import cn.redture.common.util.TraceContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Service
@org.springframework.jmx.export.annotation.ManagedResource(
        objectName = "cn.redture.sse:type=Connections")
public class SseEmitterServiceImpl implements SseEmitterService, AutoCloseable {
    private final PushConfig config;
    private final ConnectionRegistry registry = new ConnectionRegistry();
    // Includes detached connections until both writer and completion have really exited.
    private final ConcurrentMap<String, ManagedConnection> live = new ConcurrentHashMap<>();
    private final Semaphore instanceSlots;
    private final InstanceQueueBudget queueBudget;
    private final SseMetrics metrics;
    private final SseConnectionWriter writer;
    private final ScheduledExecutorService inspector;
    private final Supplier<SseEmitter> emitterFactory;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> warningSince = new HashMap<>();
    private final Set<String> activeWarnings = new HashSet<>();
    private final AtomicBoolean stopping = new AtomicBoolean();

    public SseEmitterServiceImpl(PushConfig config) {
        this(
                config,
                () -> new SseEmitter(config.getConnectionTimeoutMs()),
                QueuedEvent.defaultMapper());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SseEmitterServiceImpl(PushConfig config, ObjectMapper objectMapper) {
        this(config, () -> new SseEmitter(config.getConnectionTimeoutMs()), objectMapper);
    }

    SseEmitterServiceImpl(PushConfig config, Supplier<SseEmitter> emitterFactory) {
        this(config, emitterFactory, QueuedEvent.defaultMapper());
    }

    private SseEmitterServiceImpl(
            PushConfig config, Supplier<SseEmitter> emitterFactory, ObjectMapper objectMapper) {
        config.validate();
        this.config = config;
        this.emitterFactory = emitterFactory;
        this.objectMapper = objectMapper;
        this.instanceSlots = new Semaphore(config.getMaxConnectionsPerInstance());
        this.queueBudget = new InstanceQueueBudget(config.getMaxQueueBytesPerInstance());
        this.metrics = new SseMetrics(config.isEnableMetrics(), config.getWarningWindowMs());
        this.writer = new SseConnectionWriter(config, metrics, this::terminate);
        this.inspector =
                Executors.newSingleThreadScheduledExecutor(
                        task -> {
                            Thread thread = new Thread(task, "sse-inspector");
                            thread.setDaemon(true);
                            return thread;
                        });
        inspector.scheduleWithFixedDelay(
                this::inspectSafely,
                config.getInspectionIntervalMs(),
                config.getInspectionIntervalMs(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public SseEmitter createEmitter(Long userId) {
        Objects.requireNonNull(userId, "userId");
        if (stopping.get() || !instanceSlots.tryAcquire()) {
            metrics.increment("admission.rejected");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "SSE connection capacity exhausted");
        }
        ManagedConnection managed = null;
        boolean registered = false;
        try {
            var emitter = emitterFactory.get();
            var context =
                    new EmitterContext(
                            userId,
                            UUID.randomUUID().toString(),
                            emitter,
                            config.getQueueCapacity(),
                            queueBudget);
            var connection = new SseConnection(context);
            managed = new ManagedConnection(connection);
            var owned = managed;
            Thread thread =
                    Thread.ofVirtual()
                            .name("sse-writer-" + context.getClientId())
                            .unstarted(
                                    () -> {
                                        try {
                                            writer.run(connection);
                                        } finally {
                                            owned.writerExited.set(true);
                                            releaseIfFinished(owned);
                                        }
                                    });
            context.setWriterThread(thread);
            emitter.onCompletion(() -> terminate(connection, EMITTER_ENDED));
            emitter.onTimeout(() -> terminate(connection, CONNECTION_TIMEOUT));
            emitter.onError(
                    error ->
                            terminate(
                                    connection,
                                    switch (SendFailureClassifier.classify(error)) {
                                        case PEER_DISCONNECTED -> PEER_DISCONNECTED;
                                        case WRITE_TIMEOUT -> WRITE_TIMEOUT;
                                        case EMITTER_ENDED -> EMITTER_ENDED;
                                        case SERIALIZATION -> SERIALIZATION_FAILURE;
                                        case APPLICATION -> APPLICATION_FAILURE;
                                        case TRANSPORT -> TRANSPORT_FAILURE;
                                    }));
            live.put(context.getClientId(), managed);
            var handshake =
                    new QueuedEvent(
                            new Notification<>(
                                    "CONNECTION_ESTABLISHED", "SSE connection successful"),
                            EventPriority.CRITICAL,
                            System.currentTimeMillis(),
                            TraceContext.getTraceId(),
                            objectMapper);
            // Reserve the handshake before publication, so concurrent business dispatch cannot
            // precede it.
            if (!context.enqueue(handshake, config, 0)) {
                metrics.increment("admission.rejected");
                terminate(connection, HANDSHAKE_REJECTED);
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "SSE handshake capacity exhausted");
            }
            SseConnection victim = registry.register(connection, config.getMaxConnectionsPerUser());
            registered = true;
            metrics.increment("admission.accepted");
            log.debug("SSE registered: userId={}, clientId={}", userId, context.getClientId());
            if (victim != null) {
                metrics.increment("admission.replaced");
                terminate(victim, REPLACED);
            }
            thread.start();
            return emitter;
        } catch (RuntimeException | Error error) {
            if (managed == null) instanceSlots.release();
            else {
                live.putIfAbsent(managed.connection.context().getClientId(), managed);
                managed.writerExited.set(true);
                terminate(managed.connection, stopping.get() ? SHUTDOWN : APPLICATION_FAILURE);
                releaseIfFinished(managed);
            }
            if (!registered && stopping.get())
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "SSE service is stopping", error);
            throw error;
        }
    }

    @Override
    public DispatchResult sendToUser(Long userId, Notification<?> notification) {
        return sendToUser(userId, notification, EventPriority.forType(notification.getType()));
    }

    @Override
    public DispatchResult sendToUser(
            Long userId, Notification<?> notification, EventPriority priority) {
        long start = System.nanoTime();
        metrics.increment("notification.created");
        try {
            var targets = registry.connections(userId);
            if (targets.isEmpty()) {
                metrics.increment("notification.noActiveConnection");
                return DispatchResult.noActiveConnection();
            }
            final QueuedEvent event;
            try {
                event =
                        new QueuedEvent(
                                notification,
                                priority,
                                System.currentTimeMillis(),
                                TraceContext.getTraceId(),
                                objectMapper);
            } catch (RuntimeException error) {
                metrics.increment("notification.serializationFailed");
                throw error;
            }
            long budget = TimeUnit.MILLISECONDS.toNanos(config.getDispatchTimeoutMs());
            int enqueued = 0, full = 0, closed = 0;
            for (var target : targets) {
                long before = System.nanoTime();
                var context = target.context();
                metrics.increment("enqueue.attempted");
                var outcome =
                        context.enqueueResult(
                                event, config, Math.max(0, budget - (before - start)));
                switch (outcome) {
                    case ACCEPTED -> {
                        enqueued++;
                        metrics.increment("enqueue.accepted");
                    }
                    case CLOSED -> {
                        closed++;
                        metrics.increment("enqueue.rejectedClosed");
                    }
                    case FULL, TOO_LARGE -> {
                        full++;
                        metrics.increment("enqueue.rejectedFull");
                        if (outcome == EmitterContext.EnqueueResult.TOO_LARGE)
                            metrics.increment("enqueue.rejectedTooLarge");
                    }
                }
                if (priority == EventPriority.CRITICAL)
                    metrics.criticalAttempt(outcome != EmitterContext.EnqueueResult.ACCEPTED);
                metrics.enqueueWait(System.nanoTime() - before);
            }
            return new DispatchResult(targets.size(), enqueued, full, closed);
        } finally {
            metrics.dispatchDuration(System.nanoTime() - start);
        }
    }

    private void terminate(SseConnection connection, SseConnection.CloseReason reason) {
        if (!connection.terminate(reason)) return;
        var context = connection.context();
        context.markClosed();
        registry.remove(connection);
        metrics.increment("connection.closed." + reason.name());
        metrics.add("event.abandoned", context.getAbandonedCount());
        log.debug(
                "SSE closed: userId={}, clientId={}, reason={}, abandoned={}",
                context.getUserId(),
                context.getClientId(),
                reason,
                context.getAbandonedCount());
        var thread = context.getWriterThread();
        if (thread != null && thread != Thread.currentThread()) thread.interrupt();
        var managed = live.get(context.getClientId());
        // Completion can wait on a container write. Never run it on the shared inspector or
        // registry lock.
        Thread.ofVirtual()
                .name("sse-completion-" + context.getClientId())
                .start(
                        () -> {
                            try {
                                if (reason == APPLICATION_FAILURE
                                        || reason == SERIALIZATION_FAILURE) {
                                    context.getEmitter()
                                            .completeWithError(
                                                    new IllegalStateException(
                                                            "SSE terminated: " + reason));
                                } else context.getEmitter().complete();
                            } catch (RuntimeException error) {
                                log.debug(
                                        "SSE completion finished with container error: clientId={},"
                                                + " reason={}",
                                        context.getClientId(),
                                        reason,
                                        error);
                            } finally {
                                if (managed != null) {
                                    managed.completionExited.set(true);
                                    releaseIfFinished(managed);
                                }
                            }
                        });
    }

    private void releaseIfFinished(ManagedConnection managed) {
        if (managed.writerExited.get()
                && managed.completionExited.get()
                && managed.released.compareAndSet(false, true)) {
            live.remove(managed.connection.context().getClientId(), managed);
            instanceSlots.release();
        }
    }

    private void inspectSafely() {
        try {
            inspect();
        } catch (RuntimeException error) {
            log.warn("SSE inspection failed", error);
        }
    }

    void inspect() {
        long now = System.nanoTime();
        long oldestQueued = 0;
        int stalled = 0, orphanWriters = 0, congestedQueues = 0;
        for (var owned : live.values()) {
            var connection = owned.connection;
            var context = connection.context();
            long writeAge = connection.writeAgeNanos(now);
            if (connection.observeQueueOccupation(now, config)) congestedQueues++;
            oldestQueued = Math.max(oldestQueued, context.getOldestQueuedAgeNanos(now));
            if (context.isClosed() && !owned.writerExited.get()) orphanWriters++;
            if (config.getWriteStallTimeoutMs() > 0
                    && writeAge >= TimeUnit.MILLISECONDS.toNanos(config.getWriteStallTimeoutMs())) {
                stalled++;
                terminate(connection, WRITE_TIMEOUT);
            }
            if (context.getState() == EmitterContext.State.DRAINING
                    && connection.drainAgeNanos(now)
                            >= TimeUnit.MILLISECONDS.toNanos(config.getDrainTimeoutMs())) {
                terminate(connection, DRAIN_TIMEOUT);
            }
        }
        synchronized (warningSince) {
            var recent = metrics.recent();
            warning(
                    "capacity",
                    (double) live.size() / config.getMaxConnectionsPerInstance()
                            >= config.getCapacityWarningRatio(),
                    now);
            warning(
                    "queueDelay",
                    config.getQueueDelayWarningMs() > 0
                            && oldestQueued
                                    >= TimeUnit.MILLISECONDS.toNanos(
                                            config.getQueueDelayWarningMs()),
                    now);
            warning("writeStalled", stalled > 0, now);
            warning("writerAfterClose", orphanWriters > 0, now);
            warning("queueOccupation", congestedQueues > 0, now);
            warning(
                    "criticalRejectionRate",
                    config.getCriticalRejectionWarningRatio() > 0
                            && recent.get("criticalAttempts") >= config.getWarningMinSamples()
                            && (double) recent.get("criticalRejected")
                                            / recent.get("criticalAttempts")
                                    >= config.getCriticalRejectionWarningRatio(),
                    now);
            warning(
                    "queueP99",
                    config.getQueueDelayWarningMs() > 0
                            && recent.get("queueSamples") >= config.getWarningMinSamples()
                            && recent.get("queueP99UpperBoundMs")
                                    >= config.getQueueDelayWarningMs(),
                    now);
        }
    }

    private void warning(String code, boolean present, long now) {
        if (!present) {
            warningSince.remove(code);
            if (activeWarnings.remove(code)) {
                metrics.increment("warning.recovered." + code);
                log.info("SSE warning recovered: reason={}", code);
            }
            return;
        }
        long since = warningSince.computeIfAbsent(code, ignored -> now);
        if (now - since >= TimeUnit.MILLISECONDS.toNanos(config.getWarningSustainMs())
                && activeWarnings.add(code)) {
            metrics.increment("warning.raised." + code);
            log.warn(
                    "SSE warning: reason={}, sustainedMs={}, registered={}, liveResources={},"
                            + " budget={}, window={}, diagnostics=JMX"
                            + " cn.redture.sse:type=Connections/diagnostics",
                    code,
                    TimeUnit.NANOSECONDS.toMillis(now - since),
                    registry.size(),
                    live.size(),
                    config.getMaxConnectionsPerInstance(),
                    metrics.recent());
        }
    }

    @org.springframework.jmx.export.annotation.ManagedOperation(
            description = "SSE lifecycle, queue, write progress, counters and warnings")
    public Map<String, Object> diagnostics() {
        long now = System.nanoTime();
        long writingAge = 0, oldestQueued = 0, queued = 0, writers = 0;
        var details = new ArrayList<Map<String, Object>>();
        for (var managed : live.values()) {
            var connection = managed.connection;
            var context = connection.context();
            long writeAge = connection.writeAgeNanos(now);
            long queueAge = context.getOldestQueuedAgeNanos(now);
            String health =
                    context.isClosed()
                            ? "TERMINATED"
                            : config.getWriteStallTimeoutMs() > 0
                                            && writeAge
                                                    >= TimeUnit.MILLISECONDS.toNanos(
                                                            config.getWriteStallTimeoutMs())
                                    ? "NO_PROGRESS"
                                    : config.getQueueDelayWarningMs() > 0
                                                    && queueAge
                                                            >= TimeUnit.MILLISECONDS.toNanos(
                                                                    config.getQueueDelayWarningMs())
                                            ? "CONGESTED"
                                            : "NORMAL";
            var detail =
                    new LinkedHashMap<String, Object>(
                            Map.of(
                                    "userId",
                                    context.getUserId(),
                                    "clientId",
                                    context.getClientId(),
                                    "state",
                                    context.getState().name(),
                                    "health",
                                    health,
                                    "writeAgeNanos",
                                    writeAge,
                                    "lastSuccessfulWriteAt",
                                    connection.lastSuccessfulWriteAt(),
                                    "queueSize",
                                    context.getQueueSize(),
                                    "queueBytes",
                                    context.getQueueBytes(),
                                    "oldestQueuedAgeNanos",
                                    queueAge,
                                    "closeReason",
                                    connection.closeReason() == null
                                            ? ""
                                            : connection.closeReason().name()));
            detail.put("queueCapacity", context.queueCapacity());
            detail.put("queueHighAgeNanos", connection.queueHighAgeNanos(now));
            details.add(Collections.unmodifiableMap(detail));
            writingAge = Math.max(writingAge, connection.writeAgeNanos(now));
            oldestQueued =
                    Math.max(oldestQueued, connection.context().getOldestQueuedAgeNanos(now));
            queued += connection.context().getQueueSize();
            if (!managed.writerExited.get()) writers++;
        }
        Set<String> warnings;
        synchronized (warningSince) {
            warnings = Set.copyOf(activeWarnings);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("registeredConnections", registry.size());
        result.put("liveResources", live.size());
        result.put("writers", writers);
        result.put("writingAgeNanos", writingAge);
        result.put("oldestQueuedAgeNanos", oldestQueued);
        result.put("queuedEvents", queued);
        result.put("queuedBytes", queueBudget.usedBytes());
        result.put("counters", metrics.counters());
        result.put("latency", metrics.latency());
        result.put("recent", metrics.recent());
        result.put("warnings", warnings);
        result.put("connections", details);
        result.put("instanceConnectionBudget", config.getMaxConnectionsPerInstance());
        result.put("instanceQueueBytesBudget", config.getMaxQueueBytesPerInstance());
        return Collections.unmodifiableMap(result);
    }

    List<EmitterContext> connections(Long userId) {
        return registry.connections(userId).stream().map(SseConnection::context).toList();
    }

    @Override
    @PreDestroy
    public void close() {
        if (!stopping.compareAndSet(false, true)) return;
        var active = registry.stopAdmission();
        active.forEach(SseConnection::beginDrain);
        long start = System.nanoTime(),
                budget = TimeUnit.MILLISECONDS.toNanos(config.getDrainTimeoutMs());
        for (var managed : List.copyOf(live.values())) {
            long remaining = budget - (System.nanoTime() - start);
            if (remaining <= 0) break;
            try {
                Thread thread = managed.connection.context().getWriterThread();
                if (thread != null)
                    thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        for (var owned : live.values()) terminate(owned.connection, DRAIN_TIMEOUT);
        inspector.shutdownNow();
    }

    private static final class ManagedConnection {
        final SseConnection connection;
        final AtomicBoolean writerExited = new AtomicBoolean();
        final AtomicBoolean completionExited = new AtomicBoolean();
        final AtomicBoolean released = new AtomicBoolean();

        ManagedConnection(SseConnection connection) {
            this.connection = connection;
        }
    }
}
