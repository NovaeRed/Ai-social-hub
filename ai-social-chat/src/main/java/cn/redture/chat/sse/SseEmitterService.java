package cn.redture.chat.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseEmitterService {

    /**
     * 创建并注册一个 SSE emitter.
     *
     * @param userId 用户ID
     * @return SseEmitter 实例
     */
    SseEmitter createEmitter(Long userId);

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
