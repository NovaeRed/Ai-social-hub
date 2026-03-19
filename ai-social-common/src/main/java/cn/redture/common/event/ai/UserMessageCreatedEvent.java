package cn.redture.common.event.ai;

import java.time.OffsetDateTime;

/**
 * 用户创建消息事件，用于触发画像时间线统计。
 */
public record UserMessageCreatedEvent(Long userId, OffsetDateTime messageTime) {
}
