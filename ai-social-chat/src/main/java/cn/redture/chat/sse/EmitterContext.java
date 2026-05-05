package cn.redture.chat.sse;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    @Getter
    private final AtomicInteger slowCount = new AtomicInteger(0);

    @Getter
    private volatile boolean active = true;
    @Setter
    private Thread writerThread;

    public EmitterContext(Long userId, String clientId, SseEmitter emitter, int queueCapacity) {
        this.userId = userId;
        this.clientId = clientId;
        this.emitter = emitter;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
    }

    public void enqueue(QueuedEvent event) {
        if (!active) {
            return;
        }

        switch (event.getPriority()) {
            case CRITICAL -> {
                try {
                    if (!queue.offer(event, 100, TimeUnit.MILLISECONDS)) {
                        log.error("[警报] 丢弃关键事件 (队列已满): 用户ID={}, 类型={}",
                                userId, event.getNotification().getType());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            case NORMAL -> {
                if (!queue.offer(event)) {
                    log.warn("丢弃普通事件 (队列已满): 用户ID={}, 类型={}",
                            userId, event.getNotification().getType());
                }
            }
            case LOW -> {
                int remaining = queue.remainingCapacity();
                int total = remaining + queue.size();
                if (remaining < total * 0.2) {
                    log.debug("丢弃低优事件 (降载): 用户ID={}, 类型={}", userId, event.getNotification().getType());
                } else {
                    queue.offer(event);
                }
            }
        }
    }

    public void markInactive() {
        this.active = false;
        if (writerThread != null && writerThread.isAlive()) {
            writerThread.interrupt();
        }
    }

    public int incrementSlowCount() {
        return slowCount.incrementAndGet();
    }

    public void resetSlowCount() {
        slowCount.set(0);
    }
}
