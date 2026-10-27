package cn.redture.chat.sse;

import lombok.Getter;
import lombok.Setter;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Connection-local queue and admission state share one lock. Network operations never hold it. */
public class EmitterContext {
    @Getter private final Long userId;
    @Getter private final String clientId;
    @Getter private final SseEmitter emitter;
    @Getter private final long createTime = System.currentTimeMillis();
    @Getter private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
    @Getter private final AtomicInteger consecutiveFailures = new AtomicInteger();
    @Getter @Setter private volatile Thread writerThread;

    public enum State {
        ACTIVE,
        DRAINING,
        CLOSED
    }

    public enum EnqueueResult {
        ACCEPTED,
        FULL,
        CLOSED,
        TOO_LARGE
    }

    private volatile State state = State.ACTIVE;
    private final int queueCapacity;
    private final InstanceQueueBudget sharedBudget;
    private final ReentrantLock queueLock = new ReentrantLock();
    private final Condition notEmpty = queueLock.newCondition();
    private final Condition capacityChanged = queueLock.newCondition();
    private final Map<EventPriority, ArrayDeque<QueuedEvent>> priorityQueues =
            new EnumMap<>(EventPriority.class);
    private int queueSize;
    private long queueBytes;
    private long abandonedCount;
    private int consecutiveCritical;
    private int consecutiveNormal;

    public EmitterContext(Long userId, String clientId, SseEmitter emitter, int queueCapacity) {
        this(userId, clientId, emitter, queueCapacity, new InstanceQueueBudget(Long.MAX_VALUE));
    }

    public EmitterContext(
            Long userId,
            String clientId,
            SseEmitter emitter,
            int queueCapacity,
            InstanceQueueBudget sharedBudget) {
        if (queueCapacity <= 0)
            throw new IllegalArgumentException("queueCapacity must be positive");
        this.userId = userId;
        this.clientId = clientId;
        this.emitter = emitter;
        this.queueCapacity = queueCapacity;
        this.sharedBudget = Objects.requireNonNull(sharedBudget);
        for (EventPriority priority : EventPriority.values())
            priorityQueues.put(priority, new ArrayDeque<>());
    }

    public void touch() {
        lastActivityTime.set(System.currentTimeMillis());
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public boolean isClosed() {
        return state == State.CLOSED;
    }

    public State getState() {
        return state;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public int incrementConsecutiveFailures() {
        return consecutiveFailures.incrementAndGet();
    }

    public void resetConsecutiveFailures() {
        consecutiveFailures.set(0);
    }

    public boolean enqueue(QueuedEvent event, PushConfig config) {
        return enqueue(
                event,
                config,
                TimeUnit.MILLISECONDS.toNanos(
                        config.getPriorityStrategy().getCriticalBlockTimeoutMs()));
    }

    public boolean enqueue(QueuedEvent event, PushConfig config, long maxWaitNanos) {
        return enqueueResult(event, config, maxWaitNanos) == EnqueueResult.ACCEPTED;
    }

    public EnqueueResult enqueueResult(QueuedEvent event, PushConfig config, long maxWaitNanos) {
        Objects.requireNonNull(event);
        long waitNanos =
                event.getPriority() == EventPriority.CRITICAL
                        ? Math.min(
                                Math.max(0, maxWaitNanos),
                                TimeUnit.MILLISECONDS.toNanos(
                                        config.getPriorityStrategy().getCriticalBlockTimeoutMs()))
                        : 0;
        // The budget includes acquiring this lock, as well as waiting for a free slot.
        long started = System.nanoTime();
        try {
            if (!queueLock.tryLock(waitNanos, TimeUnit.NANOSECONDS)) return EnqueueResult.FULL;
            try {
                while (true) {
                    if (state != State.ACTIVE) return EnqueueResult.CLOSED;
                    if (event.getSizeBytes() > config.getMaxEventBytes()
                            || event.getSizeBytes() > config.getMaxQueueBytes())
                        return EnqueueResult.TOO_LARGE;
                    double threshold =
                            switch (event.getPriority()) {
                                case NORMAL ->
                                        config.getPriorityStrategy().getNormalDiscardThreshold();
                                case LOW -> config.getPriorityStrategy().getLowDiscardThreshold();
                                case CRITICAL -> 0;
                            };
                    boolean hasCapacity =
                            queueSize < queueCapacity
                                    && ((double) (queueCapacity - queueSize) / queueCapacity)
                                            >= threshold
                                    && event.getSizeBytes()
                                            <= config.getMaxQueueBytes() - queueBytes;
                    if (hasCapacity) {
                        // Instance exhaustion rejects immediately; no cross-connection queue waits.
                        if (!sharedBudget.tryReserve(event.getSizeBytes()))
                            return EnqueueResult.FULL;
                        priorityQueues
                                .get(event.getPriority())
                                .addLast(event.acceptedAt(System.nanoTime()));
                        queueSize++;
                        queueBytes += event.getSizeBytes();
                        touch();
                        notEmpty.signal();
                        return EnqueueResult.ACCEPTED;
                    }
                    long remaining = waitNanos - (System.nanoTime() - started);
                    if (remaining <= 0) return EnqueueResult.FULL;
                    capacityChanged.awaitNanos(remaining);
                }
            } finally {
                queueLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnqueueResult.FULL;
        }
    }

    public QueuedEvent pollNext(long timeout, TimeUnit unit, PushConfig.PriorityStrategy strategy)
            throws InterruptedException {
        long remaining = Math.max(0, unit.toNanos(timeout));
        queueLock.lockInterruptibly();
        try {
            while (queueSize == 0) {
                if (state != State.ACTIVE || remaining <= 0) return null;
                remaining = notEmpty.awaitNanos(remaining);
            }
            QueuedEvent event = selectNext(strategy);
            queueSize--;
            queueBytes -= event.getSizeBytes();
            sharedBudget.release(event.getSizeBytes());
            capacityChanged.signalAll();
            return event;
        } finally {
            queueLock.unlock();
        }
    }

    private QueuedEvent selectNext(PushConfig.PriorityStrategy strategy) {
        ArrayDeque<QueuedEvent> critical = priorityQueues.get(EventPriority.CRITICAL);
        ArrayDeque<QueuedEvent> normal = priorityQueues.get(EventPriority.NORMAL);
        ArrayDeque<QueuedEvent> low = priorityQueues.get(EventPriority.LOW);
        if (!critical.isEmpty()
                && (consecutiveCritical < strategy.getMaxConsecutiveCritical()
                        || (normal.isEmpty() && low.isEmpty()))) {
            consecutiveCritical++;
            return critical.removeFirst();
        }
        if (!normal.isEmpty()
                && (consecutiveNormal < strategy.getMaxConsecutiveNormal() || low.isEmpty())) {
            consecutiveCritical = 0;
            consecutiveNormal++;
            return normal.removeFirst();
        }
        if (!low.isEmpty()) {
            consecutiveCritical = 0;
            consecutiveNormal = 0;
            return low.removeFirst();
        }
        consecutiveCritical = 0;
        consecutiveNormal = 0;
        return critical.removeFirst();
    }

    public int getQueueSize() {
        queueLock.lock();
        try {
            return queueSize;
        } finally {
            queueLock.unlock();
        }
    }

    public long getQueueBytes() {
        queueLock.lock();
        try {
            return queueBytes;
        } finally {
            queueLock.unlock();
        }
    }

    public long getAbandonedCount() {
        queueLock.lock();
        try {
            return abandonedCount;
        } finally {
            queueLock.unlock();
        }
    }

    public boolean isQueueEmpty() {
        return getQueueSize() == 0;
    }

    public long getOldestQueuedAgeNanos(long now) {
        queueLock.lock();
        try {
            long oldest = Long.MAX_VALUE;
            for (ArrayDeque<QueuedEvent> queue : priorityQueues.values()) {
                QueuedEvent head = queue.peekFirst();
                if (head != null) oldest = Math.min(oldest, head.getEnqueuedAtNanos());
            }
            return oldest == Long.MAX_VALUE ? 0 : Math.max(0, now - oldest);
        } finally {
            queueLock.unlock();
        }
    }

    /** Only forward lifecycle transitions are accepted; close atomically discards and releases. */
    public boolean transitionState(State expected, State target) {
        queueLock.lock();
        try {
            if (state != expected || target.ordinal() <= state.ordinal()) return false;
            state = target;
            if (target == State.CLOSED) {
                abandonedCount += queueSize;
                for (ArrayDeque<QueuedEvent> queue : priorityQueues.values()) queue.clear();
                sharedBudget.release(queueBytes);
                queueBytes = 0;
                queueSize = 0;
            }
            notEmpty.signalAll();
            capacityChanged.signalAll();
            return true;
        } finally {
            queueLock.unlock();
        }
    }

    public void markInactive() {
        transitionState(State.ACTIVE, State.DRAINING);
    }

    public void markClosed() {
        queueLock.lock();
        try {
            if (state != State.CLOSED) transitionState(state, State.CLOSED);
        } finally {
            queueLock.unlock();
        }
    }
}
