package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AiModelVO {
    private String name;
    private String provider;
    private List<String> capabilities;
    private ModelPricingVO pricing;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
}
