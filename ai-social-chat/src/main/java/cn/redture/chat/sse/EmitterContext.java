package cn.redture.chat.sse;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class EmitterContext {

    @Getter
    private final Long userId;
    @Getter
    private final String clientId;
    @Getter
    private final SseEmitter emitter;
    private final int queueCapacity;
    /**
     * 三个队列共享这一组许可，因此任意时刻单个连接的待发送事件总数不会超过 queueCapacity。
     */
    private final Semaphore capacityPermits;
    private final Semaphore availableEvents = new Semaphore(0);
    private final Map<EventPriority, ArrayBlockingQueue<QueuedEvent>> priorityQueues =
            new EnumMap<>(EventPriority.class);
    private int consecutiveCritical;
    private int consecutiveNormal;

    public enum State {ACTIVE, DRAINING, CLOSED}

    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);

    /**
     * 状态转移：仅当当前状态等于 expected 时才更新为 newState
     *
     * @return 是否转移成功
     */
    public boolean transitionState(State expected, State newState) {
        return state.compareAndSet(expected, newState);
    }

    public boolean isActive() {
        return state.get() == State.ACTIVE;
    }

    public boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    @Getter
    private final long createTime = System.currentTimeMillis();
    @Getter
    private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());

    public void touch() {
        lastActivityTime.set(System.currentTimeMillis());
    }

    @Getter
    private final AtomicInteger slowCount = new AtomicInteger(0);
    @Getter
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public int incrementSlowCount() {
        lastFailureTime.set(System.currentTimeMillis());
        return slowCount.incrementAndGet();
    }

    public void resetSlowCount() {
        slowCount.set(0);
    }

    /**
     * 检查是否超过重置窗口，自动清零
     */
    public void checkAndResetSlowCount(long resetIntervalMs) {
        long now = System.currentTimeMillis();
        if (now - lastFailureTime.get() > resetIntervalMs) {
            resetSlowCount();
        }
    }

    @Setter
    @Getter
    private volatile Thread writerThread;

    public EmitterContext(Long userId, String clientId, SseEmitter emitter, int queueCapacity) {
        this.userId = userId;
        this.clientId = clientId;
        this.emitter = emitter;
        this.queueCapacity = queueCapacity;
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.capacityPermits = new Semaphore(queueCapacity, true);
        for (EventPriority priority : EventPriority.values()) {
            priorityQueues.put(priority, new ArrayBlockingQueue<>(queueCapacity));
        }
    }

    public boolean enqueue(QueuedEvent event, PushConfig config) {
        if (isClosed()) {
            return false;
        }

        touch(); // 更新活跃时间

        PushConfig.PriorityStrategy strategy = config.getPriorityStrategy();

        return switch (event.getPriority()) {
            case CRITICAL -> {
                try {
                    long timeout = strategy.getCriticalBlockTimeoutMs();
                    if (timeout <= 0) {
                        capacityPermits.acquire(); // 无限阻塞
                        yield offerAfterCapacityReserved(event);
                    } else {
                        boolean success = capacityPermits.tryAcquire(timeout, TimeUnit.MILLISECONDS);
                        if (!success) {
                            log.error("[警报] 丢弃关键事件(队列超时): 用户ID={}, 类型={}, traceId={}",
                                    userId, event.getNotification().getType(), event.getTraceId());
                        }
                        yield success && offerAfterCapacityReserved(event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    yield false;
                }
            }
            case NORMAL -> {
                double threshold = strategy.getNormalDiscardThreshold();
                if (shouldDiscardByThreshold(threshold)) {
                    log.debug("丢弃普通事件(队列负载高): 用户ID={}, 类型={}, remaining={}/{}",
                            userId, event.getNotification().getType(),
                            capacityPermits.availablePermits(), queueCapacity());
                    yield false;
                }
                yield tryOffer(event);
            }
            case LOW -> {
                double threshold = strategy.getLowDiscardThreshold();
                if (shouldDiscardByThreshold(threshold)) {
                    log.debug("丢弃低优事件(降载): 用户ID={}, 类型={}, remaining={}/{}",
                            userId, event.getNotification().getType(),
                            capacityPermits.availablePermits(), queueCapacity());
                    yield false;
                }
                yield tryOffer(event);
            }
        };
    }

    private boolean shouldDiscardByThreshold(double threshold) {
        return ((double) capacityPermits.availablePermits() / queueCapacity) < threshold;
    }

    private boolean tryOffer(QueuedEvent event) {
        return capacityPermits.tryAcquire() && offerAfterCapacityReserved(event);
    }

    private boolean offerAfterCapacityReserved(QueuedEvent event) {
        boolean offered = priorityQueues.get(event.getPriority()).offer(event);
        if (offered) {
            availableEvents.release();
        } else {
            capacityPermits.release();
        }
        return offered;
    }

    /**
     * 以有界优先级轮转取事件：连续发送有限条高优事件后，已积压的较低优事件必定获得一次机会。
     */
    public QueuedEvent pollNext(long timeout, TimeUnit unit, PushConfig.PriorityStrategy strategy)
            throws InterruptedException {
        if (!availableEvents.tryAcquire(timeout, unit)) {
            return null;
        }
        QueuedEvent event = selectNext(strategy);
        if (event == null) {
            // 理论上不会发生；释放许可，避免异常状态永久占用容量。
            capacityPermits.release();
            return null;
        }
        capacityPermits.release();
        return event;
    }

    private QueuedEvent selectNext(PushConfig.PriorityStrategy strategy) {
        ArrayBlockingQueue<QueuedEvent> critical = priorityQueues.get(EventPriority.CRITICAL);
        ArrayBlockingQueue<QueuedEvent> normal = priorityQueues.get(EventPriority.NORMAL);
        ArrayBlockingQueue<QueuedEvent> low = priorityQueues.get(EventPriority.LOW);

        if (!critical.isEmpty()
                && (consecutiveCritical < strategy.getMaxConsecutiveCritical() || (normal.isEmpty() && low.isEmpty()))) {
            consecutiveCritical++;
            return critical.poll();
        }
        if (!normal.isEmpty()
                && (consecutiveNormal < strategy.getMaxConsecutiveNormal() || low.isEmpty())) {
            consecutiveCritical = 0;
            consecutiveNormal++;
            return normal.poll();
        }
        if (!low.isEmpty()) {
            consecutiveCritical = 0;
            consecutiveNormal = 0;
            return low.poll();
        }
        // 高优队列为空时，重置对应配额，避免下一轮无谓让步。
        consecutiveCritical = 0;
        consecutiveNormal = 0;
        return critical.poll();
    }

    public int getQueueSize() {
        return queueCapacity - capacityPermits.availablePermits();
    }

    public boolean isQueueEmpty() {
        return getQueueSize() == 0;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public void markInactive() {
        if (transitionState(State.ACTIVE, State.DRAINING)) {
            log.debug("标记连接为DRAINING: 用户ID={}, 客户端ID={}", userId, clientId);
            Thread wt = writerThread;
            if (wt != null && !wt.isInterrupted()) {
                wt.interrupt();
            }
        }
    }

    public void markClosed() {
        if (transitionState(State.DRAINING, State.CLOSED) ||
                transitionState(State.ACTIVE, State.CLOSED)) {
            log.debug("标记连接为CLOSED: 用户ID={}, 客户端ID={}", userId, clientId);
        }
    }

    public State getState() {
        return state.get();
    }
}
