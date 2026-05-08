package cn.redture.chat.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseEmitterServiceImpl implements SseEmitterService {

    private final PushConfig pushConfig;

    // 用户ID -> (clientId -> EmitterContext)
    private final Map<Long, Map<String, EmitterContext>> userConnectionMap = new ConcurrentHashMap<>();

    @Override
    public SseEmitter createEmitter(Long userId) {
        String clientId = UUID.randomUUID().toString();

        // 原子获取或创建用户连接映射
        Map<String, EmitterContext> contexts = userConnectionMap.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

        // 1. 处理相同clientId的复用（避免重复连接）
        EmitterContext previous = contexts.get(clientId);
        if (previous != null) {
            log.info("检测到相同clientId重连，清理旧连接: 用户ID={}, 客户端ID={}", userId, clientId);
            safeCleanup(userId, clientId);
        }

        // 2. 检查连接数限制，智能淘汰
        if (contexts.size() >= pushConfig.getMaxConnectionsPerUser()) {
            String victimId = selectVictimConnection(contexts);
            if (victimId != null) {
                log.info("用户{}连接数超限({})，淘汰连接: {}",
                        userId, pushConfig.getMaxConnectionsPerUser(), victimId);
                safeCleanup(userId, victimId);
            }
        }

        // 3. 创建新连接
        SseEmitter emitter = new SseEmitter(pushConfig.getConnectionTimeoutMs());
        EmitterContext context = new EmitterContext(userId, clientId, emitter, pushConfig.getQueueCapacity());

        // 4. 注册
        contexts.put(clientId, context);
        log.info("成功创建SSE连接: 用户ID={}, 客户端ID={}", userId, clientId);

        // 5. 注册回调（统一由 safeCleanup 处理，确保幂等）
        emitter.onCompletion(() -> safeCleanup(userId, clientId));
        emitter.onTimeout(() -> {
            log.debug("SSE连接超时: 用户ID={}, 客户端ID={}", userId, clientId);
            safeCleanup(userId, clientId);
        });
        emitter.onError(e -> {
            if (!isConnectionAborted(e)) {
                log.warn("SSE连接异常: 用户ID={}, 客户端ID={}, 错误={}", userId, clientId, e.getMessage());
            }
            safeCleanup(userId, clientId);
        });

        // 6. 启动writer线程
        Thread writerThread = Thread.ofVirtual()
                .name("sse-writer-" + userId + "-" + clientId)
                .start(() -> runWriterLoop(context));
        context.setWriterThread(writerThread);

        // 7. 握手事件也走队列，由writer统一发送，避免竞态
        enqueueConnectionEstablishedEvent(context);

        return emitter;
    }

    /**
     * 将握手事件入队，由writer线程发送（避免主线程与writer线程竞态）
     */
    private void enqueueConnectionEstablishedEvent(EmitterContext context) {
        Notification<String> handshake = Notification.<String>builder()
                .type("CONNECTION_ESTABLISHED")
                .payload("SSE connection successful")
                .build();
        QueuedEvent event = new QueuedEvent(handshake, EventPriority.CRITICAL, System.currentTimeMillis(), shortTraceId());

        // 握手事件必须成功入队，否则连接无意义
        boolean enqueued = context.enqueue(event, pushConfig);
        if (!enqueued) {
            log.error("握手事件入队失败，立即关闭连接: 用户ID={}, 客户端ID={}",
                    context.getUserId(), context.getClientId());
            safeCleanup(context.getUserId(), context.getClientId());
        }
    }

    /**
     * 幂等清理，避免重复释放资源
     */
    private void safeCleanup(Long userId, String clientId) {
        Map<String, EmitterContext> contexts = userConnectionMap.get(userId);
        if (contexts == null) {
            return;
        }

        EmitterContext context = contexts.remove(clientId);
        if (context == null) {
            return;
        }

        context.markClosed();

        // 执行一次性资源释放
        try {
            // 1. 标记inactive，中断writer
            context.markInactive();

            // 2. 关闭emitter（幂等调用）
            SseEmitter emitter = context.getEmitter();
            if (emitter != null) {
                try {
                    emitter.complete();
                } catch (IllegalStateException ignored) {
                    // emitter可能已被其他回调关闭
                }
            }

            // 3. 中断writer线程（如果还在运行）
            Thread wt = context.getWriterThread();
            if (wt != null && wt.isAlive() && !wt.isInterrupted()) {
                wt.interrupt();
            }

            log.debug("SSE连接资源释放完成: 用户ID={}, 客户端ID={}", userId, clientId);

        } catch (Exception e) {
            log.error("清理连接时发生异常: 用户ID={}, 客户端ID={}", userId, clientId, e);
        } finally {
            // 原子清理空的用户映射，避免内存泄漏
            userConnectionMap.compute(userId, (k, v) ->
                    (v == null || v.isEmpty()) ? null : v);
        }
    }

    /**
     * 智能选择待淘汰连接
     * 策略：非活跃 > 队列积压多 > 创建时间早
     */
    private String selectVictimConnection(Map<String, EmitterContext> contexts) {
        return contexts.entrySet().stream()
                .min(Comparator
                        // 1. 优先淘汰非活跃连接（最后活跃时间最早）
                        .comparingLong((Map.Entry<String, EmitterContext> e) -> e.getValue().getLastActivityTime().get())
                        // 2. 其次淘汰队列积压多的（消费能力差）
                        .thenComparingInt(e -> e.getValue().getQueue().size())
                        // 3. 最后淘汰创建时间早的（老连接）
                        .thenComparingLong(e -> e.getValue().getCreateTime())
                )
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    @Override
    public void sendToUser(Long userId, Notification<?> notification) {
        EventPriority priority = resolvePriority(notification.getType());
        sendToUser(userId, notification, priority);
    }

    @Override
    public void sendToUser(Long userId, Notification<?> notification, EventPriority priority) {
        Map<String, EmitterContext> contexts = userConnectionMap.get(userId);
        if (contexts == null || contexts.isEmpty()) {
            log.debug("用户{}无活跃连接，事件丢弃: type={}", userId, notification.getType());
            return;
        }

        QueuedEvent event = new QueuedEvent(notification, priority, System.currentTimeMillis(), shortTraceId());
        int successCount = 0;

        for (EmitterContext ctx : contexts.values()) {
            if (ctx.isActive() && ctx.enqueue(event, pushConfig)) {
                ctx.touch(); // 更新活跃时间
                successCount++;
            }
        }

        if (successCount == 0) {
            log.debug("用户{}的所有连接均未成功入队事件: type={}", userId, notification.getType());
        }
    }

    private EventPriority resolvePriority(String type) {
        if (type == null) return EventPriority.NORMAL;
        String upper = type.toUpperCase();
        if (upper.contains("CRITICAL") || upper.contains("MESSAGE") ||
                upper.contains("FRIEND") || upper.contains("CONNECTION")) {
            return EventPriority.CRITICAL;
        } else if (upper.contains("STATUS") || upper.contains("READ") || upper.contains("TYPING")) {
            return EventPriority.LOW;
        }
        return EventPriority.NORMAL;
    }

    /**
     * Writer主循环：统一处理事件发送 + 心跳 + 慢消费者检测
     */
    private void runWriterLoop(EmitterContext context) {
        long lastHeartbeat = System.currentTimeMillis();
        long heartbeatInterval = pushConfig.getHeartbeatIntervalMs();
        long pollTimeout = pushConfig.getQueuePollTimeoutMs();
        long resetInterval = pushConfig.getSlowCountResetIntervalMs();

        try {
            while (!context.isClosed() && !Thread.currentThread().isInterrupted()) {

                // 1. 检查慢消费者计数是否需要重置
                context.checkAndResetSlowCount(resetInterval);

                // 2. 尝试获取事件（带超时，便于心跳和状态检查）
                QueuedEvent event = context.getQueue().poll(pollTimeout, TimeUnit.MILLISECONDS);

                if (event != null) {
                    // 3. 发送事件
                    if (sendEventSafely(context, event)) {
                        context.resetSlowCount();
                        context.touch();
                    }
                } else {
                    // 4. 无事件时发送心跳（避免客户端断开）
                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeat >= heartbeatInterval && context.isActive()) {
                        sendHeartbeat(context);
                        lastHeartbeat = now;
                    }
                    continue;
                }

                // 5. 如果处于DRAINING状态且队列为空，优雅退出
                if (context.getState() == EmitterContext.State.DRAINING && context.getQueue().isEmpty()) {
                    log.debug("连接处于DRAINING状态且队列已空，优雅退出: 用户ID={}", context.getUserId());
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Writer线程被中断: 用户ID={}, 客户端ID={}", context.getUserId(), context.getClientId());
        } catch (Exception e) {
            log.error("Writer循环未预期异常: 用户ID={}, 客户端ID={}",
                    context.getUserId(), context.getClientId(), e);
        } finally {
            // 最终清理（safeCleanup已幂等，可安全调用）
            safeCleanup(context.getUserId(), context.getClientId());
        }
    }

    /**
     * 安全发送单个事件，处理各种异常
     */
    private boolean sendEventSafely(EmitterContext context, QueuedEvent event) {
        try {
            context.getEmitter().send(SseEmitter.event()
                    .name(event.getNotification().getType())
                    .data(event.getNotification()));
            return true;
        } catch (IOException e) {
            if (isConnectionAborted(e)) {
                log.debug("连接已断开，停止发送: 用户ID={}, 错误={}", context.getUserId(), e.getMessage());
                return false;
            }
            log.warn("发送IO异常: 用户ID={}, traceId={}, 错误={}",
                    context.getUserId(), event.getTraceId(), e.getMessage());
            handleSendFailure(context);
            return false;
        } catch (IllegalStateException e) {
            log.debug("Emitter已关闭: 用户ID={}", context.getUserId());
            return false;
        } catch (Exception e) {
            log.error("发送未预期异常: 用户ID={}, traceId={}",
                    context.getUserId(), event.getTraceId(), e);
            handleSendFailure(context);
            return false;
        }
    }

    private void sendHeartbeat(EmitterContext context) {
        try {
            context.getEmitter().send(SseEmitter.event()
                    .name("HEARTBEAT")
                    .data(Notification.builder()
                            .type("HEARTBEAT")
                            .payload(String.valueOf(System.currentTimeMillis()))
                            .build()));
        } catch (IOException e) {
            if (!isConnectionAborted(e)) {
                log.warn("心跳发送失败: 用户ID={}", context.getUserId());
            }
            handleSendFailure(context);
        }
    }

    private boolean isConnectionAborted(Throwable e) {
        if (e == null) {
            return false;
        }
        String msg = e.getMessage();
        return msg != null && (
                msg.contains("Broken pipe") ||
                        msg.contains("Connection reset") ||
                        msg.contains("Connection timed out") ||
                        msg.contains("ClientAbortException")
        );
    }

    private String shortTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void handleSendFailure(EmitterContext context) {
        int failures = context.incrementSlowCount();
        int maxFailures = pushConfig.getMaxSlowCount();

        if (failures >= maxFailures) {
            // 熔断前先尝试排水关键事件
            drainCriticalEvents(context);

            log.error("[熔断保护] 连续发送失败{}次，断开连接: 用户ID={}, 客户端ID={}",
                    maxFailures, context.getUserId(), context.getClientId());

            context.markInactive();
            safeCleanup(context.getUserId(), context.getClientId());

            try {
                context.getEmitter().completeWithError(new RuntimeException("Slow client circuit breaker"));
            } catch (Exception ignore) {
                // 可能已关闭
            }
        }
    }

    /**
     * 尝试快速消费队列中的关键事件，减少消息丢失
     */
    private void drainCriticalEvents(EmitterContext context) {
        QueuedEvent event;
        int drained = 0;
        while ((event = context.getQueue().poll()) != null && drained < 10) {
            if (event.getPriority() == EventPriority.CRITICAL) {
                try {
                    context.getEmitter().send(SseEmitter.event()
                            .name(event.getNotification().getType())
                            .data(event.getNotification()));
                    drained++;
                } catch (Exception ignore) {
                    break; // 发送失败则停止
                }
            }
        }
        if (drained > 0) {
            log.info("熔断前排水关键事件{}条: 用户ID={}", drained, context.getUserId());
        }
    }
}