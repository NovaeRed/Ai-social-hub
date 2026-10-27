package cn.redture.chat.sse;

import cn.redture.common.integration.notification.DispatchResult;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 提供事件流连接创建和通知入队服务。 */
public interface SseEmitterService {

    /**
     * 创建并注册用户的事件流连接。
     *
     * @param userId 连接所属用户 ID
     * @return 新建事件流连接
     */
    SseEmitter createEmitter(Long userId);

    /**
     * 按事件类型确定优先级，并向用户连接入队通知。
     *
     * @param userId 目标用户 ID
     * @param notification 待分发通知
     * @return 连接级入队结果，不表示客户端送达
     */
    DispatchResult sendToUser(Long userId, Notification<?> notification);

    /**
     * 按指定优先级向用户连接入队通知。
     *
     * @param userId 目标用户 ID
     * @param notification 待分发通知
     * @param priority 事件优先级
     * @return 连接级入队结果，不表示客户端送达
     */
    DispatchResult sendToUser(Long userId, Notification<?> notification, EventPriority priority);
}
