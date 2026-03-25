package cn.redture.aiEngine.pojo.dto;

import lombok.Data;

import java.util.Map;

/**
 * 统一异步任务载荷（NOTIFICATION_TASK 领域）。
 */
@Data
public class NotificationAsyncTaskDTO {

    private Long userId;

    private String eventType;

    private Map<String, Object> payload;
}
