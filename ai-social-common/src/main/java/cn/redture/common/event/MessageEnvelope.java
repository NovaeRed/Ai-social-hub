package cn.redture.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标准消息信封
 * @param <T> 具体业务载荷的类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEnvelope<T> {
    /** 选填的网关或所属领域 */
    private String domain;
    /** 链路追踪ID */
    private String traceId;
    /** 事件或业务类型 */
    private String eventType;
    /** 消息版本号，用于向下兼容 */
    private String version;
    /** 产生时间戳 (毫秒级) */
    private Long timestamp;
    /** 业务操作标识 */
    private String bizId;
    /** 用户ID */
    private Long userId;
    /** 消费重试次数 */
    private Integer retryCount;
    /** 强类型的业务数据体 */
    private T payload;
}