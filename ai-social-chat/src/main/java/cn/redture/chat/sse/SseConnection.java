package cn.redture.chat.sse;

import java.util.concurrent.atomic.AtomicReference;

/** Transport lifecycle and progress, independent of registry membership. */
final class SseConnection {
    enum CloseReason {
        REPLACED,
        PEER_DISCONNECTED,
        EMITTER_ENDED,
        CONNECTION_TIMEOUT,
        WRITE_TIMEOUT,
        SERIALIZATION_FAILURE,
        TRANSPORT_FAILURE,
        APPLICATION_FAILURE,
        SHUTDOWN,
        DRAIN_TIMEOUT,
        HANDSHAKE_REJECTED
    }

    private final EmitterContext context;
    private final AtomicReference<CloseReason> closeReason = new AtomicReference<>();
    private volatile long writeStartedAtNanos;
    private volatile long lastSuccessfulWriteAtNanos = System.nanoTime();
    private volatile long lastSuccessfulWriteAt;
    private volatile long drainStartedAtNanos;
    private volatile long queueHighSinceNanos;

    SseConnection(EmitterContext context) {
        this.context = context;
    }

    EmitterContext context() {
        return context;
    }

    CloseReason closeReason() {
        return closeReason.get();
    }

    synchronized boolean terminate(CloseReason reason) {
        return closeReason.compareAndSet(null, reason);
    }

    synchronized boolean writeStarted() {
        if (closeReason.get() != null) return false;
        writeStartedAtNanos = System.nanoTime();
        return true;
    }

    void writeFinished(boolean success) {
        if (success) {
            lastSuccessfulWriteAtNanos = System.nanoTime();
            lastSuccessfulWriteAt = System.currentTimeMillis();
            context.resetConsecutiveFailures();
            context.touch();
        }
        writeStartedAtNanos = 0;
    }

    long writeAgeNanos(long now) {
        long started = writeStartedAtNanos;
        return started == 0 ? 0 : now - started;
    }

    long sinceSuccessfulWriteNanos(long now) {
        return now - lastSuccessfulWriteAtNanos;
    }

    long lastSuccessfulWriteAt() {
        return lastSuccessfulWriteAt;
    }

    boolean observeQueueOccupation(long now, PushConfig config) {
        double ratio =
                Math.max(
                        (double) context.getQueueSize() / context.queueCapacity(),
                        (double) context.getQueueBytes() / config.getMaxQueueBytes());
        boolean high = !context.isClosed() && ratio >= config.getCapacityWarningRatio();
        if (!high) queueHighSinceNanos = 0;
        else if (queueHighSinceNanos == 0) queueHighSinceNanos = now;
        return high;
    }

    long queueHighAgeNanos(long now) {
        long since = queueHighSinceNanos;
        return since == 0 ? 0 : Math.max(0, now - since);
    }

    void beginDrain() {
        drainStartedAtNanos = System.nanoTime();
        context.markInactive();
    }

    long drainAgeNanos(long now) {
        return drainStartedAtNanos == 0 ? 0 : now - drainStartedAtNanos;
    }
}
