package cn.redture.aiEngine.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 智能回复请求
 * <p>
 * 两种使用模式：
 * 1. 自动模式：提供 conversationPublicId，后端自动选择最近10条消息作为上下文
 * 2. 手动模式：提供 conversationHistory，前端准备好历史消息并传递给后端
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SmartReplyRequest extends BaseAiTaskRequest {

    /**
     * 对方发送的最新消息
     */
    private String message;

    /**
     * 会话的 public ID
     */
    @JsonProperty("conversation_public_id")
    private String conversationPublicId;

    /**
     * 对话历史（手动模式，可选）
     */
    @JsonProperty("conversation_history")
    private List<HistoryMessage> conversationHistory;

    /**
     * 当前用户（回复者）画像（可选）
     */
    @JsonProperty("user_profile")
    private UserProfile userProfile;

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