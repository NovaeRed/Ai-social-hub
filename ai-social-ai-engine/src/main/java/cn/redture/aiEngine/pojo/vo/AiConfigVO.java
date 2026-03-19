package cn.redture.aiEngine.pojo.vo;

import cn.redture.aiEngine.pojo.model.AiConfigParams;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AiConfigVO {
    @JsonProperty("selected_model_option_code")
    private String selectedModelOptionCode;
    private List<AiModelVO> modelOptions;
    private AiConfigParams switches;
    private UserUsageSummaryVO usage;
}
