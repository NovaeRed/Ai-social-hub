package cn.redture.chat.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseEmitterServiceImpl implements SseEmitterService {

    private final PushConfig pushConfig;

    private final Map<Long, Map<String, EmitterContext>> userConnectionMap = new ConcurrentHashMap<>();

    @Override
    public SseEmitter createEmitter(Long userId) {
        String clientId = UUID.randomUUID().toString();

        Map<String, EmitterContext> contexts = userConnectionMap.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

        if (contexts.size() >= pushConfig.getMaxConnectionsPerUser()) {
            log.warn("用户 {} 达到最大连接数限制 {}，拒绝新连接。", userId, pushConfig.getMaxConnectionsPerUser());
            String keyToRemove = contexts.keySet().iterator().next();
            cleanup(userId, keyToRemove);
        }

        SseEmitter emitter = new SseEmitter(pushConfig.getConnectionTimeoutMs());
        EmitterContext context = new EmitterContext(userId, clientId, emitter, pushConfig.getQueueCapacity());

        EmitterContext previous = contexts.put(clientId, context);
        if (previous != null) {
            previous.markInactive();
            previous.getEmitter().complete();
        }

        log.info("成功为用户创建SSE连接: 用户ID={}, 客户端ID={}", userId, clientId);

        emitter.onCompletion(() -> cleanup(userId, clientId));
        emitter.onTimeout(() -> cleanup(userId, clientId));
        emitter.onError(e -> {
            log.warn("用户的SSE连接出错: 用户ID={}, 客户端ID={}: {}", userId, clientId, e.getMessage());
            cleanup(userId, clientId);
        });

        Thread writerThread = Thread.ofVirtual().name("sse-writer-" + userId + "-" + clientId).start(() -> runWriterLoop(context));
        context.setWriterThread(writerThread);

        sendToUser(userId, Notification.builder()
                .type("CONNECTION_ESTABLISHED")
                .payload("SSE connection successful")
                .build(), EventPriority.CRITICAL);

        return emitter;
    }

    private void cleanup(Long userId, String clientId) {
        Map<String, EmitterContext> contexts = userConnectionMap.get(userId);
        if (contexts == null) return;

        EmitterContext context = contexts.remove(clientId);
        if (context != null) {
            context.markInactive();
            log.debug("清理SSE连接完成: 用户ID={}, 客户端ID={}", userId, clientId);
        }

        if (contexts.isEmpty()) {
            userConnectionMap.remove(userId, contexts);
        }
    }

    @Override
    public void sendToUser(Long userId, Notification<?> notification) {
        EventPriority priority = resolvePriority(notification.getType());
        sendToUser(userId, notification, priority);
    }

    @Override
    public void sendToUser(Long userId, Notification<?> notification, EventPriority priority) {
        Map<String, EmitterContext> contexts = userConnectionMap.get(userId);
        if (contexts == null || contexts.isEmpty()) {
            log.debug("用户 {} 没有活跃的SSE连接，无法发送 {} 事件", userId, notification.getType());
            return;
        }

        QueuedEvent event = new QueuedEvent(notification, priority, System.currentTimeMillis());

        // 广播到用户的所有活跃连接，使得多端设备都能及时收到通知
        for (EmitterContext ctx : contexts.values()) {
            if (ctx.isActive()) {
                ctx.enqueue(event);
            }
        }
    }

    private EventPriority resolvePriority(String type) {
        if (type == null) return EventPriority.NORMAL;
        String upperType = type.toUpperCase();
        if (upperType.contains("CRITICAL") || upperType.contains("MESSAGE") || upperType.contains("FRIEND") || upperType.contains("CONNECTION")) {
            return EventPriority.CRITICAL;
        } else if (upperType.contains("STATUS") || upperType.contains("READ")) {
            return EventPriority.LOW;
        }
        return EventPriority.NORMAL;
    }

    private void runWriterLoop(EmitterContext context) {
        log.debug("写线程已启动: 用户ID={}, 客户端ID={}", context.getUserId(), context.getClientId());
        try {
            while (context.isActive() && !Thread.currentThread().isInterrupted()) {
                QueuedEvent event = context.getQueue().take(); // blocking
                if (!context.isActive()) break;

                try {
                    context.getEmitter().send(SseEmitter.event().data(event.getNotification()));
                    context.resetSlowCount();
                } catch (IOException e) {
                    log.warn("向用户发送事件发生IO错误: 用户ID={}, 客户端ID={}: {}", context.getUserId(), context.getClientId(), e.getMessage());
                    handleSendFailure(context);
                } catch (Exception e) {
                    log.error("向用户发送事件发生未知错误: 用户ID={}, 客户端ID={}", context.getUserId(), context.getClientId(), e);
                    handleSendFailure(context);
                }
            }
        } catch (InterruptedException e) {
            log.debug("写线程被中断: 用户ID={}, 客户端ID={}", context.getUserId(), context.getClientId());
            Thread.currentThread().interrupt();
        } finally {
            log.debug("写线程即将退出: 用户ID={}, 客户端ID={}", context.getUserId(), context.getClientId());
            cleanup(context.getUserId(), context.getClientId());
        }
    }

    private void handleSendFailure(EmitterContext context) {
        int failures = context.incrementSlowCount();
        if (failures >= pushConfig.getMaxSlowCount()) {
            log.error("[熔断保护] 连接失败次数过多，断开连接: 用户ID={}, 客户端ID={}",
                    context.getUserId(), context.getClientId());
            context.markInactive();
            cleanup(context.getUserId(), context.getClientId());
            try {
                context.getEmitter().completeWithError(new RuntimeException("Slow client circuit breaker"));
            } catch (Exception ignore) {
            }
        }
    }
}
