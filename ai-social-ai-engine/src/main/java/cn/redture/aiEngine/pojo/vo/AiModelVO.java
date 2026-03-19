package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class AiModelVO {
    @JsonProperty("option_code")
    private String optionCode;
    @JsonProperty("display_name")
    private String displayName;
    private String name;
    private String provider;
    private List<String> capabilities;
    private ModelPricingVO pricing;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
}
