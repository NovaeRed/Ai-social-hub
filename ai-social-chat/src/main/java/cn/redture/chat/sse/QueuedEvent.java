package cn.redture.chat.sse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueuedEvent {
    private Notification<?> notification;
    private EventPriority priority;
    private long timestamp;
}