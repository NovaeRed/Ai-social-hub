package cn.redture.aiEngine.pojo.vo;

import cn.redture.aiEngine.pojo.model.AiConfigParams;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AiConfigVO {
    @JsonProperty("default_model")
    private String defaultModel;
    private List<String> providers;
    private AiConfigParams preferences;
    private UserUsageSummaryVO usage;
}
