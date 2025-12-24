package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PersonaAnalysisVO {
    @JsonProperty("task_public_id")
    private String taskPublicId;
}
