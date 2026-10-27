package cn.redture.chat.sse;

import java.util.concurrent.atomic.AtomicReference;

/** 管理连接生命周期、首次关闭原因和发送进展。 */
final class SseConnection {
    enum CloseReason {
        REPLACED,
        PEER_DISCONNECTED,
        EMITTER_ENDED,
        CONNECTION_TIMEOUT,
        WRITE_TIMEOUT,
        SERIALIZATION_FAILURE,
        TRANSPORT_FAILURE,
        APPLICATION_FAILURE,
        SHUTDOWN,
        DRAIN_TIMEOUT,
        HANDSHAKE_REJECTED
    }

    private final EmitterContext context;
    private final AtomicReference<CloseReason> closeReason = new AtomicReference<>();
    private volatile long writeStartedAtNanos;
    private volatile long lastSuccessfulWriteAtNanos = System.nanoTime();
    private volatile long lastSuccessfulWriteAt;
    private volatile long drainStartedAtNanos;
    private volatile long queueHighSinceNanos;

    SseConnection(EmitterContext context) {
        this.context = context;
    }

    EmitterContext context() {
        return context;
    }

    CloseReason closeReason() {
        return closeReason.get();
    }

    /**
     * 记录首次终止原因，阻止后续写入开始。
     *
     * @param reason 本次终止原因
     * @return 是否首次终止，重复终止时为 false
     */
    synchronized boolean terminate(CloseReason reason) {
        return closeReason.compareAndSet(null, reason);
    }

    /**
     * 未终止时登记本次写入的开始时间。
     *
     * @return 是否允许开始写入
     */
    synchronized boolean writeStarted() {
        if (closeReason.get() != null) return false;
        writeStartedAtNanos = System.nanoTime();
        return true;
    }

    /**
     * 清除写入标记，成功时更新进展并重置连续失败次数。
     *
     * @param success 本次写入是否成功
     */
    void writeFinished(boolean success) {
        if (success) {
            lastSuccessfulWriteAtNanos = System.nanoTime();
            lastSuccessfulWriteAt = System.currentTimeMillis();
            context.resetConsecutiveFailures();
            context.touch();
        }
        writeStartedAtNanos = 0;
    }

    /**
     * 计算当前未结束写入的持续时间。
     *
     * @param now 当前单调时间，单位为纳秒
     * @return 写入持续纳秒数，未在写入时为 0
     */
    long writeAgeNanos(long now) {
        long started = writeStartedAtNanos;
        return started == 0 ? 0 : now - started;
    }

    /**
     * 计算距最近成功写入的时间，首写前以创建时刻为基准。
     *
     * @param now 当前单调时间，单位为纳秒
     * @return 距基准时刻的纳秒数
     */
    long sinceSuccessfulWriteNanos(long now) {
        return now - lastSuccessfulWriteAtNanos;
    }

    long lastSuccessfulWriteAt() {
        return lastSuccessfulWriteAt;
    }

    /**
     * 检查数量或字节占用是否达到阈值，并维护高占用起点。
     *
     * @param now 当前单调时间，单位为纳秒
     * @param config 队列字节上限及容量预警配置
     * @return 未关闭队列是否处于高占用状态
     */
    boolean observeQueueOccupation(long now, PushConfig config) {
        double ratio =
                Math.max(
                        (double) context.getQueueSize() / context.queueCapacity(),
                        (double) context.getQueueBytes() / config.getMaxQueueBytes());
        boolean high = !context.isClosed() && ratio >= config.getCapacityWarningRatio();
        if (!high) queueHighSinceNanos = 0;
        else if (queueHighSinceNanos == 0) queueHighSinceNanos = now;
        return high;
    }

    /**
     * 计算本轮持续高占用的时间。
     *
     * @param now 当前单调时间，单位为纳秒
     * @return 持续高占用纳秒数，未处于高占用时为 0
     */
    long queueHighAgeNanos(long now) {
        long since = queueHighSinceNanos;
        return since == 0 ? 0 : Math.max(0, now - since);
    }

    /** 记录排空起点，并停止接纳新事件。 */
    void beginDrain() {
        drainStartedAtNanos = System.nanoTime();
        context.markInactive();
    }

    /**
     * 计算限时排空已持续的时间。
     *
     * @param now 当前单调时间，单位为纳秒
     * @return 排空持续纳秒数，尚未开始时为 0
     */
    long drainAgeNanos(long now) {
        return drainStartedAtNanos == 0 ? 0 : now - drainStartedAtNanos;
    }
}
