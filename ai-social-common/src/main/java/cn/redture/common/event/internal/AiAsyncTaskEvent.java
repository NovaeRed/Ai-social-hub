package cn.redture.common.event.internal;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * AI 异步任务分发事件 (Spring ApplicationEvent)
 * 用于将 Redis Consumer 收到的消息解耦至各个业务 Handler
 */
@Getter
public class AiAsyncTaskEvent extends ApplicationEvent {

    private final String domain;
    private final String eventType;
    private final Long userId;
    private final String taskJsonPayload;
    private final String recordId;

    public AiAsyncTaskEvent(Object source, String domain, String eventType, Long userId, String taskJsonPayload, String recordId) {
        super(source);
        this.domain = domain;
        this.eventType = eventType;
        this.userId = userId;
        this.taskJsonPayload = taskJsonPayload;
        this.recordId = recordId;
    }
}