package cn.redture.aiEngine.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 内容总结请求 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SummarizeRequest extends BaseAiTaskRequest {

    /**
     * 需要总结的内容
     */
    private String content;

    /**
     * 总结类型 (meeting, chat, article, email)
     */
    @JsonProperty("summary_type")
    private String summaryType;

    /**
     * 目标长度 (short, medium, long)
     */
    @JsonProperty("target_length")
    private String targetLength;

    /**
     * 关键词列表
     */
    private List<String> keywords;

    /**
     * 会话公开ID（未直接提供 content 时可用于自动选取最近消息）
     */
    @JsonProperty("conversation_public_id")
    private String conversationPublicId;

    /**
     * 用户显式选择的消息ID列表（优先于 conversation_public_id）
     */
    @JsonProperty("selected_message_ids")
    private List<Long> selectedMessageIds;
}
