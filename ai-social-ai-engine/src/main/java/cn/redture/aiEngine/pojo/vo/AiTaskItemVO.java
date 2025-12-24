package cn.redture.aiEngine.pojo.vo;

import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.model.ModelConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class AiTaskItemVO {

    @JsonProperty("public_id")
    private String publicId;

    private AiTaskType type;

    private AiTaskStatus status;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("completed_at")
    private OffsetDateTime completedAt;

    private Map<String, Object> result;

    @JsonProperty("model_config")
    private ModelConfig modelConfig;
}
