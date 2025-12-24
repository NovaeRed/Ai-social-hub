package cn.redture.aiEngine.pojo.vo;

import cn.redture.aiEngine.pojo.model.ModelConfig;
import cn.redture.aiEngine.pojo.model.TokenUsage;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * AI任务详情VO
 */
@Data
public class AiTaskDetailVO {
    @JsonProperty("public_id")
    private String publicId;

    private String type;

    private String status;

    @JsonProperty("input_payload")
    private Map<String, Object> inputPayload;

    @JsonProperty("output_payload")
    private Map<String, Object> outputPayload;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("model_config")
    private ModelConfig modelConfig;

    @JsonProperty("token_usage")
    private TokenUsage tokenUsage;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("completed_at")
    private OffsetDateTime completedAt;
}
