package cn.redture.chat.sse;

import cn.redture.common.integration.notification.DispatchResult;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseEmitterService {

    SseEmitter createEmitter(Long userId);

    DispatchResult sendToUser(Long userId, Notification<?> notification);

    DispatchResult sendToUser(Long userId, Notification<?> notification, EventPriority priority);
}
