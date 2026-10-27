package cn.redture.chat.sse;

import jakarta.annotation.PostConstruct;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** SSE 连接、队列、传输和预警配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "chat.sse.push")
public class PushConfig {

    // 连接管理
    private int maxConnectionsPerUser = 5;
    private int maxConnectionsPerInstance = 2000;
    private long connectionTimeoutMs = 1_800_000; // 30分钟

    // 队列配置
    private int queueCapacity = 50;
    private long queuePollTimeoutMs = 500; // 写入线程的轮询间隔
    private long maxEventBytes = 1_048_576;
    private long maxQueueBytes = 8_388_608;
    private long maxQueueBytesPerInstance = 268_435_456;
    private long dispatchTimeoutMs = 100;
    private long drainTimeoutMs = 2000;

    // 熔断保护
    private int maxSlowCount = 3; // 连续发送失败阈值

    /**
     * @deprecated 连续失败仅由成功写入重置，保留旧配置兼容性。
     */
    @Deprecated private long slowCountResetIntervalMs = 10_000;

    // 优先级丢弃策略
    private PriorityStrategy priorityStrategy = new PriorityStrategy();

    /** 优先级丢弃阈值及连续发送配额。 */
    @Data
    public static class PriorityStrategy {
        // CRITICAL: 队列满时最多阻塞等待时间(毫秒)，0 表示立即返回
        private long criticalBlockTimeoutMs = 100;

        // NORMAL: 队列剩余容量低于此比例时开始丢弃 [0.0 ~ 1.0]
        private double normalDiscardThreshold = 0.0; // 0表示满时才丢弃

        // LOW: 队列剩余容量低于此比例时开始丢弃
        private double lowDiscardThreshold = 0.2; // 剩余<20%时丢弃低优事件

        // 公平调度：达到连续配额后，若较低优队列有积压，必须让出一个发送机会
        private int maxConsecutiveCritical = 5;
        private int maxConsecutiveNormal = 3;
    }

    // 心跳与监控
    private long heartbeatIntervalMs = 30_000;
    private boolean enableMetrics = true; // 是否启用指标上报
    private long inspectionIntervalMs = 250;
    // 0 为仅观测；启用终止预算时必须同时配置容器传输超时。
    private long writeStallTimeoutMs = 0;
    private long transportWriteTimeoutMs = 0;
    private long queueDelayWarningMs = 0;
    private double capacityWarningRatio = 0.8;
    private long warningSustainMs = 30_000;
    private long warningWindowMs = 60_000;
    private int warningMinSamples = 100;
    private double criticalRejectionWarningRatio = 0;

    /** 校验配置范围及容量、超时之间的关联约束。 */
    @PostConstruct
    public void validate() {
        if (maxConnectionsPerUser <= 0
                || maxConnectionsPerInstance <= 0
                || queueCapacity <= 0
                || connectionTimeoutMs <= 0
                || queuePollTimeoutMs <= 0
                || heartbeatIntervalMs <= 0
                || inspectionIntervalMs <= 0
                || drainTimeoutMs <= 0
                || maxSlowCount <= 0
                || dispatchTimeoutMs < 0
                || maxEventBytes <= 0
                || maxQueueBytes < maxEventBytes
                || maxQueueBytesPerInstance < maxEventBytes
                || writeStallTimeoutMs < 0
                || transportWriteTimeoutMs < 0
                || transportWriteTimeoutMs > Integer.MAX_VALUE
                || (writeStallTimeoutMs > 0 && transportWriteTimeoutMs <= 0)
                || queueDelayWarningMs < 0
                || warningSustainMs < 0
                || warningWindowMs < 12
                || warningMinSamples <= 0
                || !validRatio(criticalRejectionWarningRatio)
                || !Double.isFinite(capacityWarningRatio)
                || capacityWarningRatio <= 0
                || capacityWarningRatio > 1
                || priorityStrategy == null
                || priorityStrategy.criticalBlockTimeoutMs < 0
                || priorityStrategy.maxConsecutiveCritical <= 0
                || priorityStrategy.maxConsecutiveNormal <= 0
                || !validRatio(priorityStrategy.normalDiscardThreshold)
                || !validRatio(priorityStrategy.lowDiscardThreshold)) {
            throw new IllegalArgumentException(
                    "Invalid chat.sse.push limits or timeout configuration");
        }
    }

    private static boolean validRatio(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
