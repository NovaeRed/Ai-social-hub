package cn.redture.chat.sse;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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
    @Getter
    private final BlockingQueue<QueuedEvent> queue;
    private final int queueCapacity;

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
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
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
                        queue.put(event); // 无限阻塞
                        yield true;
                    } else {
                        boolean success = queue.offer(event, timeout, TimeUnit.MILLISECONDS);
                        if (!success) {
                            log.error("[警报] 丢弃关键事件(队列超时): 用户ID={}, 类型={}, traceId={}",
                                    userId, event.getNotification().getType(), event.getTraceId());
                        }
                        yield success;
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
                            queue.remainingCapacity(), queueCapacity());
                    yield false;
                }
                yield queue.offer(event);
            }
            case LOW -> {
                double threshold = strategy.getLowDiscardThreshold();
                if (shouldDiscardByThreshold(threshold)) {
                    log.debug("丢弃低优事件(降载): 用户ID={}, 类型={}, remaining={}/{}",
                            userId, event.getNotification().getType(),
                            queue.remainingCapacity(), queueCapacity());
                    yield false;
                }
                yield queue.offer(event);
            }
        };
    }

    private boolean shouldDiscardByThreshold(double threshold) {
        int remaining = queue.remainingCapacity();
        int total = remaining + queue.size();
        return total > 0 && ((double) remaining / total) < threshold;
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