package cn.redture.common.integration.notification;

import java.util.Map;

/** 统一通知外部服务接口（由聚合层实现）。 */
public interface NotificationExternalService {

    /**
     * 向指定用户的在线连接入队状态变更提示。 返回连接级接纳结果；不表示网络写入、客户端收取或用户已读。 持久化业务结果由业务查询接口提供，离线或容量拒绝是已处置的实时提示结果。
     *
     * @param userId 用户 ID
     * @param eventType 事件类型
     * @param payload 事件载荷
     * @return 各目标连接的入队和拒绝计数，不表示客户端送达
     */
    DispatchResult sendToUser(Long userId, String eventType, Map<String, Object> payload);
}
