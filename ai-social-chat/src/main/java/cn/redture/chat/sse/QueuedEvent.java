package cn.redture.chat.sse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 保存不可变事件快照及连接级入队时间。 */
@Getter
public final class QueuedEvent {
    private static final ObjectMapper DEFAULT_MAPPER = defaultMapper();
    private final EventPriority priority;
    private final long timestamp;
    private final String traceId;
    private final String eventId;
    private final String type;
    private final String serializedNotification;
    private final long sizeBytes;
    private final long enqueuedAtNanos;

    @Getter(lombok.AccessLevel.NONE)
    private final JsonNode payloadSnapshot;

    /**
     * 创建使用 ISO 日期且忽略空字段的默认序列化器。
     *
     * @return 默认 JSON 序列化器
     */
    static ObjectMapper defaultMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }

    public QueuedEvent(
            Notification<?> notification, EventPriority priority, long timestamp, String traceId) {
        this(notification, priority, timestamp, traceId, DEFAULT_MAPPER);
    }

    public QueuedEvent(
            Notification<?> notification,
            EventPriority priority,
            long timestamp,
            String traceId,
            ObjectMapper mapper) {
        this.priority = Objects.requireNonNull(priority, "priority");
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.eventId = java.util.UUID.randomUUID().toString();
        this.type = Objects.requireNonNull(notification, "notification").getType();
        this.enqueuedAtNanos = System.nanoTime();
        try {
            JsonNode snapshot = mapper.valueToTree(notification);
            this.payloadSnapshot = snapshot.get("payload");
            this.serializedNotification = mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalArgumentException("SSE event cannot be serialized", e);
        }
        this.sizeBytes = serializedNotification.getBytes(StandardCharsets.UTF_8).length;
    }

    private QueuedEvent(QueuedEvent source, long enqueuedAtNanos) {
        this.priority = source.priority;
        this.timestamp = source.timestamp;
        this.traceId = source.traceId;
        this.eventId = source.eventId;
        this.type = source.type;
        this.serializedNotification = source.serializedNotification;
        this.sizeBytes = source.sizeBytes;
        this.payloadSnapshot = source.payloadSnapshot;
        this.enqueuedAtNanos = enqueuedAtNanos;
    }

    /**
     * 复用事件快照，记录当前连接实际接受入队的时刻。
     *
     * @param now 当前单调时间，单位为纳秒
     * @return 带有本连接入队时间的事件
     */
    QueuedEvent acceptedAt(long now) {
        return new QueuedEvent(this, now);
    }

    /**
     * 生成通知的防御性副本，避免外部修改影响已排队事件。
     *
     * @return 包含载荷副本的通知
     */
    public Notification<?> getNotification() {
        return new Notification<>(
                type, payloadSnapshot == null ? null : payloadSnapshot.deepCopy());
    }
}
