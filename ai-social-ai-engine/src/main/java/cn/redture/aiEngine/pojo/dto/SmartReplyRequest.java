// SmartReplyRequest.java
package cn.redture.aiEngine.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class SmartReplyRequest extends BaseAiTaskRequest {

    /**
     * 对方发送的最新消息
     */
    private String message;

    /**
     * 对话历史（可选）
     */
    @JsonProperty("conversation_history")
    private List<HistoryMessage> conversationHistory;

    /**
     * 当前用户（回复者）画像（可选）
     */
    @JsonProperty("user_profile")
    private UserProfile userProfile;

    // --- 内部类 ---
    @Data
    public static class HistoryMessage {
        private String sender;
        private String content;
        private OffsetDateTime timestamp;
    }

    @Data
    public static class UserProfile {
        private String name;
        private String role;
        @JsonProperty("communication_style")
        private String communicationStyle;
    }
}