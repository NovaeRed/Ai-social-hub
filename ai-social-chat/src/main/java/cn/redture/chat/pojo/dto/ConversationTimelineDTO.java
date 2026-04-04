package cn.redture.chat.pojo.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ConversationTimelineDTO {
    private Long id;
    private String publicId;
    private String type;
    private String name;
    private Long latestMessageId;
    private String latestMessagePublicId;
    private String latestMessageContent;
    private OffsetDateTime latestMessageCreatedAt;
    private Long unreadCount;
}
