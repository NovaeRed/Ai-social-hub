package cn.redture.aiEngine.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonaAnalysisRequest extends BaseAiTaskRequest {
    private List<MessageItem> messages;
    @JsonProperty("target_user_id")
    private String targetUserId;
    @JsonProperty("analysis_config")
    private Map<String, Object> analysisConfig;
}
