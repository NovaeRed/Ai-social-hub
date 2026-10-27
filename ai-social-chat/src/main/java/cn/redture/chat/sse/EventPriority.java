package cn.redture.chat.sse;

import java.util.Locale;

/** 事件优先级及类型映射。 */
public enum EventPriority {
    CRITICAL,
    NORMAL,
    LOW;

    /**
     * 按明确的事件名称映射优先级。
     *
     * @param type 事件类型，可为 null
     * @return 匹配的优先级，空值或未知类型为 NORMAL
     */
    public static EventPriority forType(String type) {
        if (type == null) return NORMAL;
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CONNECTION_ESTABLISHED",
                    "MESSAGE_CREATED",
                    "MESSAGE_DELETED",
                    "MESSAGE_UPDATED",
                    "FRIEND_REQUEST_NEW",
                    "FRIEND_REQUEST_ACCEPTED",
                    "FRIEND_REMOVED",
                    "AI_TASK_COMPLETED",
                    "AI_TASK_FAILED" ->
                    CRITICAL;
            case "MESSAGE_READ",
                    "MESSAGE_READ_UPDATED",
                    "TYPING",
                    "TYPING_STATUS",
                    "USER_STATUS",
                    "HEARTBEAT" ->
                    LOW;
            default -> NORMAL;
        };
    }
}
