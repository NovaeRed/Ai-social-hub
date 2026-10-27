package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import cn.redture.common.event.AiTaskCompletedEvent;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.integration.notification.DispatchResult;
import cn.redture.common.integration.notification.NotificationExternalService;
import cn.redture.common.util.JsonUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.Map;

/** 通知任务处理器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotificationTaskHandler implements AiTaskHandler {

    private final NotificationExternalService notificationExternalService;

    @Override
    public AsyncTaskDomain getDomain() {
        return AsyncTaskDomain.NOTIFICATION_TASK;
    }

    @Override
    public void executeTask(String taskJson, Long userId, String eventType, String recordId)
            throws Exception {
        AiTaskCompletedEvent payload = JsonUtil.fromJson(taskJson, AiTaskCompletedEvent.class);
        if (payload == null || payload.getAiTaskId() == null) {
            throw new InvalidInputException("NOTIFICATION_TASK 载荷不完整: " + recordId);
        }
        handle(userId, eventType, payload);
    }

    /**
     * 处理通知任务。
     *
     * @param userId 用户 ID
     * @param eventType 事件类型
     * @param payload 事件载荷
     */
    public void handle(Long userId, String eventType, AiTaskCompletedEvent payload) {
        if (userId == null || eventType == null || eventType.isBlank()) {
            throw new InvalidInputException("NOTIFICATION_TASK 缺少用户或事件类型");
        }
        try {
            Map<String, Object> mapPayload =
                    JsonUtil.fromJson(JsonUtil.toJson(payload), java.util.Map.class);
            DispatchResult result =
                    notificationExternalService.sendToUser(userId, eventType, mapPayload);
            String outcome =
                    switch (result.status()) {
                        case NO_ACTIVE_CONNECTION -> "无在线连接";
                        case ENQUEUED -> "已入队";
                        case PARTIALLY_ENQUEUED -> "部分连接已入队";
                        case REJECTED_FULL -> "容量不足，未入队";
                        case REJECTED_CLOSED -> "连接已关闭，未入队";
                    };
            log.info(
                    "[NOTIFICATION_TASK] 实时提示{}（不表示客户端送达）, userId={}, eventType={}, status={},"
                            + " targets={}, enqueued={}, rejectedFull={}, rejectedClosed={}",
                    outcome,
                    userId,
                    eventType,
                    result.status(),
                    result.targetConnections(),
                    result.enqueuedConnections(),
                    result.rejectedFull(),
                    result.rejectedClosed());
        } catch (Exception e) {
            log.error("[NOTIFICATION_TASK] 通知推送失败, userId={}, eventType={}", userId, eventType, e);
            throw e;
        }
    }
}
