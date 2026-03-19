package cn.redture.chat.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseEmitterService {

    /**
     * 创建并注册一个 SSE emitter.
     *
     * @param userId 用户ID
     * @param clientId 客户端标识（同一用户多端在线时用于区分连接）
     * @return SseEmitter 实例
     */
    SseEmitter createEmitter(Long userId, String clientId);

    /**
     * 创建并注册一个 SSE emitter.
     *
     * @param userId 用户ID
     * @return SseEmitter 实例
     */
    default SseEmitter createEmitter(Long userId) {
        return createEmitter(userId, null);
    }

    /**
     * 向指定用户发送事件.
     *
     * @param userId 用户ID
     * @param notification  要发送的通知对象
     */
    void sendToUser(Long userId, Notification<?> notification);

    /**
     * 从注册中移除一个 emitter.
     *
     * @param userId 用户ID
     */
    void removeEmitter(Long userId);
}
