package cn.redture.chat.sse;

import java.util.Locale;

public enum EventPriority {
    CRITICAL,
    NORMAL,
    LOW;

    /** Exact protocol names; unknown event types use NORMAL. */
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
