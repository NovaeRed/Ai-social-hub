package cn.redture.chat.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class SseEmitterServiceImpl implements SseEmitterService {

    private static final Long DEFAULT_TIMEOUT = 30 * 60 * 1000L; // 30分钟

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public SseEmitter createEmitter(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.put(userId, emitter);
        log.debug("用户 {} 的 SSE emitter 已创建.", userId);

        // 生命周期清理
        emitter.onCompletion(() -> cleanup(userId));
        emitter.onTimeout(() -> cleanup(userId));
        emitter.onError(e -> {
            log.warn("用户 {} 的 SSE emitter 发生错误: {}", userId, e.getMessage());
            cleanup(userId);
        });

        // 发送连接成功事件
        sendToUser(userId, Notification.builder()
                .type("CONNECTION_ESTABLISHED")
                .payload("SSE connection successful")
                .build());

        return emitter;
    }

    private void cleanup(Long userId) {
        SseEmitter removed = emitters.remove(userId);
        if (removed != null) {
            log.debug("用户 {} 的 SSE emitter 已清理.", userId);
        }
    }

    @Override
    public void sendToUser(Long userId, Notification<?> notification) {
        log.debug("准备向用户 {} 发送通知，类型: {}", userId, notification.getType());

        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.warn("用户 {} 不存在 SSE emitter", userId);
            return;
        }

        virtualExecutor.submit(() -> {
            log.debug("虚拟线程开始处理用户 {} 的通知发送", userId);

            // 对同一个 emitter 加锁，确保串行发送
            synchronized (emitter) {
                try {
                    // 再次检查（防止在排队期间 emitter 被关闭）
                    if (emitters.containsKey(userId)) {
                        log.debug("向用户 {} 发送 SSE 事件: {}", userId, notification.getType());
                        emitter.send(SseEmitter.event().data(notification));
                        log.debug("成功向用户 {} 发送通知", userId);
                    } else {
                        log.warn("用户 {} 的 emitter 已被移除，跳过发送", userId);
                    }
                } catch (IOException e) {
                    log.error("向用户 {} 发送 SSE 事件失败: {}", userId, e.getMessage(), e);
                    cleanup(userId);
                } catch (Exception e) {
                    log.error("向用户 {} 发送 SSE 事件发生未知错误: {}", userId, e.getMessage(), e);
                    cleanup(userId);
                }
            }

            log.debug("虚拟线程完成用户 {} 的通知发送", userId);
        });

        log.debug("已提交用户 {} 的通知发送任务", userId);
    }

    @Override
    public void removeEmitter(Long userId) {
        cleanup(userId);
    }
}