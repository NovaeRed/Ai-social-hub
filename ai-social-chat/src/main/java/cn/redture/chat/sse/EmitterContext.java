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
                    // 阻塞上限控制在2秒，避免过多占用
                    if (!queue.offer(event, 2, TimeUnit.SECONDS)) {
                        log.error("[警报] 丢弃关键事件 (队列已满): 用户ID={}, 客户端ID={}, 类型={}",
                                userId, clientId, event.getNotification().getType());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("关键事件入队操作被中断", e);
                }
            }
            case NORMAL -> {
                if (!queue.offer(event)) {
                    log.warn("丢弃普通事件 (队列已满): 用户ID={}, 客户端ID={}, 类型={}",
                            userId, clientId, event.getNotification().getType());
                }
            }
            case LOW -> {
                // 如果剩余空间极小，开始限流丢弃
                int remaining = queue.remainingCapacity();
                int total = remaining + queue.size();
                if (remaining < total * 0.2) {
                    log.debug("丢弃低优事件 (降载): 用户ID={}, 客户端ID={}, 类型={}",
                            userId, clientId, event.getNotification().getType());
                } else {
                    if (!queue.offer(event)) {
                        log.debug("丢弃低优事件 (队列刚满): 用户ID={}", userId);
                    }
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
