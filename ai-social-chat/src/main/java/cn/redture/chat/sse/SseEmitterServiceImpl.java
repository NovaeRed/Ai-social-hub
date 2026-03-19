package cn.redture.chat.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class SseEmitterServiceImpl implements SseEmitterService {

    private static final Long DEFAULT_TIMEOUT = 30 * 60 * 1000L; // 30分钟

    private final Map<Long, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public SseEmitter createEmitter(Long userId, String clientId) {
        String normalizedClientId = normalizeClientId(clientId);
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        Map<String, SseEmitter> userEmitters = emitters.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        SseEmitter previous = userEmitters.put(normalizedClientId, emitter);
        if (previous != null) {
            previous.complete();
        }

        log.debug("用户 {} 的 SSE emitter 已创建, clientId={}", userId, normalizedClientId);

        // 生命周期清理
        emitter.onCompletion(() -> cleanup(userId, normalizedClientId));
        emitter.onTimeout(() -> cleanup(userId, normalizedClientId));
        emitter.onError(e -> {
            log.warn("用户 {} 的 SSE emitter 发生错误: {}, clientId={}", userId, e.getMessage(), normalizedClientId);
            cleanup(userId, normalizedClientId);
        });

        // 发送连接成功事件
        sendToEmitter(userId, normalizedClientId, emitter,
                Notification.builder()
                        .type("CONNECTION_ESTABLISHED")
                        .payload("SSE connection successful")
                        .build());

        return emitter;
    }

    private String normalizeClientId(String clientId) {
        return (clientId == null || clientId.isBlank()) ? UUID.randomUUID().toString() : clientId.trim();
    }

    private void cleanup(Long userId, String clientId) {
        Map<String, SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }

        SseEmitter removed = userEmitters.remove(clientId);
        if (removed != null) {
            log.debug("用户 {} 的 SSE emitter 已清理, clientId={}", userId, clientId);
        }

        if (userEmitters.isEmpty()) {
            emitters.remove(userId, userEmitters);
        }
    }

    @Override
    public void sendToUser(Long userId, Notification<?> notification) {
        log.debug("准备向用户 {} 发送通知，类型: {}", userId, notification.getType());

        Map<String, SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.warn("用户 {} 不存在 SSE emitter", userId);
            return;
        }

        for (Map.Entry<String, SseEmitter> entry : userEmitters.entrySet()) {
            sendToEmitter(userId, entry.getKey(), entry.getValue(), notification);
        }

        log.debug("已提交用户 {} 的通知发送任务", userId);
    }

    private void sendToEmitter(Long userId, String clientId, SseEmitter emitter, Notification<?> notification) {
        virtualExecutor.submit(() -> {
            log.debug("虚拟线程开始处理用户 {} 的通知发送, clientId={}", userId, clientId);

            synchronized (emitter) {
                try {
                    Map<String, SseEmitter> userEmitters = emitters.get(userId);
                    if (userEmitters != null && userEmitters.get(clientId) == emitter) {
                        log.debug("向用户 {} 发送 SSE 事件: {}, clientId={}", userId, notification.getType(), clientId);
                        emitter.send(SseEmitter.event().data(notification));
                        log.debug("成功向用户 {} 发送通知, clientId={}", userId, clientId);
                    } else {
                        log.warn("用户 {} 的 emitter 已被移除，跳过发送, clientId={}", userId, clientId);
                    }
                } catch (IOException e) {
                    log.error("向用户 {} 发送 SSE 事件失败: {}, clientId={}", userId, e.getMessage(), clientId, e);
                    cleanup(userId, clientId);
                } catch (Exception e) {
                    log.error("向用户 {} 发送 SSE 事件发生未知错误: {}, clientId={}", userId, e.getMessage(), clientId, e);
                    cleanup(userId, clientId);
                }
            }

            log.debug("虚拟线程完成用户 {} 的通知发送, clientId={}", userId, clientId);
        });
    }

    @Override
    public void removeEmitter(Long userId) {
        Map<String, SseEmitter> removedUserEmitters = emitters.remove(userId);
        if (removedUserEmitters == null || removedUserEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : removedUserEmitters.values()) {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }
}