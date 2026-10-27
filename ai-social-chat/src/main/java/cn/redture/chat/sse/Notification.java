package cn.redture.chat.sse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 通知载荷。
 *
 * @param <T> 载荷数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification<T> {

    /** 事件类型, 例如 MESSAGE_CREATED, FRIEND_REQUEST_NEW */
    private String type;

    /** 事件关联的数据载体 */
    private T payload;
}
