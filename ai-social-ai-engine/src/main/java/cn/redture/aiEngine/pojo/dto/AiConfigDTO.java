package cn.redture.aiEngine.pojo.dto;

import cn.redture.aiEngine.pojo.model.AiConfigParams;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AiConfigDTO {
    @JsonProperty("default_model")
    private String defaultModel;
    
    @JsonProperty("default_provider")
    private String defaultProvider;
    
    private AiConfigParams preferences;
}
