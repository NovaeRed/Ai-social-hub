package cn.redture.aiEngine.pojo.dto;

import cn.redture.aiEngine.pojo.model.AiConfigParams;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AiConfigDTO {
    @JsonProperty("model_option_code")
    private String modelOptionCode;

    private AiConfigParams preferences;
}
