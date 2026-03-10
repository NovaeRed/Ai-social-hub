package cn.redture.aiEngine.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 个人画像分析请求
 * 
 * 特点：用户显式选择要分析的消息，强调隐私保护
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonaAnalysisRequest extends BaseAiTaskRequest {
    
    /**
     * 用户选择的消息ID列表
     */
    @JsonProperty("selected_message_ids")
    private List<Long> selectedMessageIds;

    /**
     * 消息对象列表
     */
    private List<MessageItem> messages;

    /**
     * 目标用户ID（可选）
     */
    @JsonProperty("target_user_id")
    private String targetUserId;

    /**
     * 分析配置（可选）
     */
    @JsonProperty("analysis_config")
    private Map<String, Object> analysisConfig;
}
