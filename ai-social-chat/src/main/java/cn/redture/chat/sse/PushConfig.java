package cn.redture.chat.sse;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chat.sse.push")
public class PushConfig {

    // 连接管理
    private int maxConnectionsPerUser = 5;
    private long connectionTimeoutMs = 1_800_000; // 30分钟

    // 队列配置
    private int queueCapacity = 50;
    private long queuePollTimeoutMs = 500;        // writer轮询间隔

    // 熔断保护
    private int maxSlowCount = 3;                 // 连续发送失败阈值
    private long slowCountResetIntervalMs = 10_000; // 失败计数重置窗口

    // 优先级丢弃策略
    private PriorityStrategy priorityStrategy = new PriorityStrategy();

    @Data
    public static class PriorityStrategy {
        // CRITICAL: 队列满时最多阻塞等待时间(毫秒), 0=无限等待
        private long criticalBlockTimeoutMs = 100;

        // NORMAL: 队列剩余容量低于此比例时开始丢弃 [0.0 ~ 1.0]
        private double normalDiscardThreshold = 0.0;  // 0表示满时才丢弃

        // LOW: 队列剩余容量低于此比例时开始丢弃
        private double lowDiscardThreshold = 0.2;     // 剩余<20%时丢弃低优事件
    }

    // 心跳与监控
    private long heartbeatIntervalMs = 30_000;
    private boolean enableMetrics = true;  // 是否启用指标上报
}