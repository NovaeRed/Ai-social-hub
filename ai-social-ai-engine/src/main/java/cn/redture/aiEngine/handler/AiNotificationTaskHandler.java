package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.dto.NotificationAsyncTaskDTO;
import cn.redture.common.integration.notification.NotificationExternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知任务处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotificationTaskHandler {

    private final NotificationExternalService notificationExternalService;

    /**
     * 处理通知任务。
     *
     * @param task 通知任务载荷
     */
    public void handle(NotificationAsyncTaskDTO task) {
        if (task == null || task.getUserId() == null) {
            return;
        }
        try {
            notificationExternalService.sendToUser(task.getUserId(), task.getEventType(), task.getPayload());
            log.info("[NOTIFICATION_TASK] 通知已推送, userId={}, eventType={}", task.getUserId(), task.getEventType());
        } catch (Exception e) {
            log.error("[NOTIFICATION_TASK] 通知推送失败, userId={}, eventType={}", task.getUserId(), task.getEventType(), e);
            throw e;
        }
    }
}
