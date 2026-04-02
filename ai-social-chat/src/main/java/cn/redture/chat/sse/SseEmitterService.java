package cn.redture.chat.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseEmitterService {

    SseEmitter createEmitter(Long userId);

    void sendToUser(Long userId, Notification<?> notification);

    void sendToUser(Long userId, Notification<?> notification, EventPriority priority);
}
