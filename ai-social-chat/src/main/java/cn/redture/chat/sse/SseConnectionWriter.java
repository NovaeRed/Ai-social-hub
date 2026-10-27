package cn.redture.chat.sse;

import static cn.redture.chat.sse.SseConnection.CloseReason.*;

import cn.redture.common.util.TraceContext;

import lombok.extern.slf4j.Slf4j;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** 作为单连接唯一写入者，串行发送业务事件和心跳。 */
@Slf4j
final class SseConnectionWriter {
    private final PushConfig config;
    private final SseMetrics metrics;
    private final BiConsumer<SseConnection, SseConnection.CloseReason> terminate;

    SseConnectionWriter(
            PushConfig config,
            SseMetrics metrics,
            BiConsumer<SseConnection, SseConnection.CloseReason> terminate) {
        this.config = config;
        this.metrics = metrics;
        this.terminate = terminate;
    }

    /**
     * 串行发送事件和空闲心跳，退出时触发连接清理。
     *
     * @param connection 当前写入线程负责的连接
     */
    void run(SseConnection connection) {
        var context = connection.context();
        metrics.increment("writer.started");
        try {
            while (!context.isClosed()) {
                long now = System.nanoTime();
                if (context.getState() == EmitterContext.State.DRAINING
                        && (context.isQueueEmpty()
                                || connection.drainAgeNanos(now)
                                        >= TimeUnit.MILLISECONDS.toNanos(
                                                config.getDrainTimeoutMs()))) break;
                long untilHeartbeat =
                        TimeUnit.MILLISECONDS.toNanos(config.getHeartbeatIntervalMs())
                                - connection.sinceSuccessfulWriteNanos(now);
                long pollNanos =
                        Math.min(
                                TimeUnit.MILLISECONDS.toNanos(config.getQueuePollTimeoutMs()),
                                Math.max(0, untilHeartbeat));
                var event =
                        context.pollNext(
                                pollNanos, TimeUnit.NANOSECONDS, config.getPriorityStrategy());
                if (context.isClosed()) {
                    if (event != null) metrics.increment("event.abandoned");
                    break;
                }
                if (event != null) {
                    metrics.queueWait(System.nanoTime() - event.getEnqueuedAtNanos());
                    if (!tryStartSend(
                            connection,
                            SseEmitter.event()
                                    .name(event.getType())
                                    .data(event.getSerializedNotification()),
                            event.getEventId(),
                            event.getTraceId(),
                            "CONNECTION_ESTABLISHED".equals(event.getType())))
                        metrics.increment("event.abandoned");
                } else if (context.isActive()
                        && connection.sinceSuccessfulWriteNanos(System.nanoTime())
                                >= TimeUnit.MILLISECONDS.toNanos(config.getHeartbeatIntervalMs())) {
                    tryStartSend(
                            connection,
                            SseEmitter.event()
                                    .name("HEARTBEAT")
                                    .data(
                                            new Notification<>(
                                                    "HEARTBEAT", System.currentTimeMillis())),
                            "heartbeat",
                            null,
                            false);
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            metrics.increment("writer.unexpectedFailure");
            log.warn(
                    "SSE writer failed: userId={}, clientId={}",
                    context.getUserId(),
                    context.getClientId(),
                    error);
            terminate.accept(connection, APPLICATION_FAILURE);
        } finally {
            terminate.accept(connection, context.isQueueEmpty() ? SHUTDOWN : DRAIN_TIMEOUT);
            metrics.increment("writer.exited");
        }
    }

    /**
     * 统一发送事件或心跳，分类统计失败并按原因终止连接。
     *
     * @param connection 目标连接
     * @param event 待写入的事件帧
     * @param eventId 事件标识，用于诊断
     * @param traceId 链路标识，心跳可为 null
     * @param handshake 是否为握手事件
     * @return 是否已尝试发送；true 不代表发送成功
     */
    private boolean tryStartSend(
            SseConnection connection,
            SseEmitter.SseEventBuilder event,
            String eventId,
            String traceId,
            boolean handshake) {
        var context = connection.context();
        if (context.isClosed() || !connection.writeStarted()) return false;
        long start = System.nanoTime();
        boolean success = false;
        if (traceId != null) TraceContext.setTraceId(traceId);
        metrics.increment("write.attempted");
        if (handshake) metrics.increment("handshake.attempted");
        try {
            context.getEmitter().send(event);
            success = true;
            metrics.increment("write.succeeded");
            if (handshake) metrics.increment("handshake.succeeded");
        } catch (Exception error) {
            var failure = SendFailureClassifier.classify(error);
            log.debug(
                    "SSE write failed: userId={}, clientId={}, eventId={}, traceId={}, reason={}",
                    context.getUserId(),
                    context.getClientId(),
                    eventId,
                    traceId,
                    failure);
            metrics.increment("write.failed." + failure.name());
            if (handshake) metrics.increment("handshake.failed");
            int failures = context.incrementConsecutiveFailures();
            switch (failure) {
                case PEER_DISCONNECTED -> terminate.accept(connection, PEER_DISCONNECTED);
                case EMITTER_ENDED -> terminate.accept(connection, EMITTER_ENDED);
                case WRITE_TIMEOUT -> terminate.accept(connection, WRITE_TIMEOUT);
                case TRANSPORT -> terminate.accept(connection, TRANSPORT_FAILURE);
                // 仅应用或格式故障允许继续发送，达到连续失败阈值后终止。
                case SERIALIZATION, APPLICATION -> {
                    if (failures >= config.getMaxSlowCount())
                        terminate.accept(
                                connection,
                                failure == SendFailureClassifier.Failure.SERIALIZATION
                                        ? SERIALIZATION_FAILURE
                                        : APPLICATION_FAILURE);
                }
            }
        } finally {
            connection.writeFinished(success);
            metrics.writeDuration(System.nanoTime() - start);
            TraceContext.clear();
        }
        return true;
    }
}
