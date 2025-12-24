package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 性格分析结果结构化对象
 */
@Data
public class PersonaAnalysisResultVO {
    /**
     * 性格类型（如：外向、内向、理性、感性等）
     */
    private String personality;

    /**
     * 性格特征列表
     */
    private List<String> traits;

    /**
     * 沟通风格描述
     */
    @JsonProperty("communication_style")
    private String communicationStyle;

    /**
     * 兴趣爱好列表
     */
    private List<String> interests;
}
