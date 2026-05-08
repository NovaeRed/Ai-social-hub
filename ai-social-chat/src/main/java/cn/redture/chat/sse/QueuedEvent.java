package cn.redture.chat.sse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueuedEvent {
    private Notification<?> notification;
    private EventPriority priority;
    private long timestamp;

    // 用于链路追踪和日志定位
    private String traceId = UUID.randomUUID().toString().substring(0, 8);
}