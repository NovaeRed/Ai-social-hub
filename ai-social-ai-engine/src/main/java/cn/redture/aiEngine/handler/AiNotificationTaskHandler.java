package cn.redture.aiEngine.handler;

import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.integration.notification.NotificationExternalService;
import cn.redture.common.event.AiTaskCompletedEvent;
import cn.redture.common.util.JsonUtil;
import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通知任务处理器。
 */
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
    public void executeTask(String taskJson, Long userId, String eventType, String recordId) throws Exception {
        AiTaskCompletedEvent payload = JsonUtil.fromJson(
                taskJson, AiTaskCompletedEvent.class
        );
        if (payload == null || payload.getAiTaskId() == null) {
            throw new InvalidInputException("NOTIFICATION_TASK 载荷不完整: " + recordId);
        }
        handle(userId, eventType, payload);
    }

    /**
     * 处理通知任务。
     *
     * @param userId    用户 ID
     * @param eventType 事件类型
     * @param payload   事件载荷
     */
    public void handle(Long userId, String eventType, AiTaskCompletedEvent payload) {
        if (userId == null) {
            return;
        }
        try {
            Map<String, Object> mapPayload = JsonUtil.fromJson(JsonUtil.toJson(payload), java.util.Map.class);
            notificationExternalService.sendToUser(userId, eventType, mapPayload);
            log.info("[NOTIFICATION_TASK] 通知已推送, userId={}, eventType={}", userId, eventType);
        } catch (Exception e) {
            log.error("[NOTIFICATION_TASK] 通知推送失败, userId={}, eventType={}", userId, eventType, e);
            throw e;
        }
    }
}
