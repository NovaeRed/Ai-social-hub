package cn.redture.chat.sse;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EventPriority {
    CRITICAL("critical", 100),   // 最高优先级，队列满时阻塞等待
    NORMAL("normal", 50),        // 普通优先级，队列满时直接丢弃
    LOW("low", 10);              // 低优先级，队列负载高时选择性丢弃

    private final String code;
    private final int weight;    // 权重值，用于优先级调度
}