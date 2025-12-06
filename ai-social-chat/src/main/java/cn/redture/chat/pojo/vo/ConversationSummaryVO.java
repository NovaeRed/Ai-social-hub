package cn.redture.chat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ConversationSummaryVO {

    @JsonProperty("public_id")
    private String publicId;

    private String type;

    private String name;

    @JsonProperty("unread_count")
    private Long unreadCount;

    @JsonProperty("latest_message")
    private LatestMessageVO latestMessage;

    @Data
    public static class LatestMessageVO {
        @JsonProperty("public_id")
        private String publicId;

        private String content;

        @JsonProperty("created_at")
        private OffsetDateTime createdAt;
    }
}
