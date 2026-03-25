package cn.redture.common.integration.notification;

import java.util.Map;

/**
 * 统一通知外部服务接口（由聚合层实现）。
 */
public interface NotificationExternalService {

    /**
     * 向指定用户推送通知事件。
     *
     * @param userId 用户 ID
     * @param eventType 事件类型
     * @param payload 事件载荷
     */
    void sendToUser(Long userId, String eventType, Map<String, Object> payload);
}
