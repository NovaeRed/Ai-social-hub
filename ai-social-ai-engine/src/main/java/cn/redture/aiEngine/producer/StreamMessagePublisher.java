package cn.redture.aiEngine.producer;

import cn.redture.common.event.MessageEnvelope;
import cn.redture.common.util.JsonUtil;
import cn.redture.common.util.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamMessagePublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String MESSAGE_VERSION_V1 = "1.0";

    /**
     * @param streamKey 投递的 Redis Stream 键名
     * @param envelope 完整的消息信封（泛型）
     */
    public <T> void publish(String streamKey, MessageEnvelope<T> envelope) {
        try {
            // 自动补齐基础设施信息
            if (envelope.getTraceId() == null) {
                envelope.setTraceId(TraceContext.getTraceId());
            }
            if (envelope.getVersion() == null) {
                envelope.setVersion(MESSAGE_VERSION_V1);
            }
            if (envelope.getTimestamp() == null) {
                envelope.setTimestamp(System.currentTimeMillis());
            }
            if (envelope.getRetryCount() == null) {
                envelope.setRetryCount(0);
            }

            // 装箱扁平化为 Redis Stream Record 要求的数据格式
            Map<String, String> recordMap = new HashMap<>();
            recordMap.put("domain", envelope.getDomain());
            recordMap.put("trace_id", envelope.getTraceId()); 
            recordMap.put("version", envelope.getVersion());
            recordMap.put("biz_id", envelope.getBizId());
            if (envelope.getEventType() != null) {
                recordMap.put("event_type", envelope.getEventType());
            }
            if (envelope.getUserId() != null) {
                recordMap.put("user_id", String.valueOf(envelope.getUserId()));
            }
            recordMap.put("retry_count", String.valueOf(envelope.getRetryCount()));
            recordMap.put("created_at_epoch", String.valueOf(envelope.getTimestamp() / 1000));
            recordMap.put("task", JsonUtil.toJson(envelope.getPayload())); 

            stringRedisTemplate.opsForStream()
                    .add(StreamRecords.string(recordMap).withStreamKey(streamKey));

        } catch (Exception e) {
            log.error("Failed to serialize message payload, stream: {}", streamKey, e);
            throw new RuntimeException("Stream Payload Serialization failed", e);
        }
    }
}