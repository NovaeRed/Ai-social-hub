package cn.redture.aiEngine.pojo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI Token使用统计
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {
    /**
     * 输入Token数
     */
    @JsonProperty("input_tokens")
    private Integer inputTokens;

    /**
     * 输出Token数
     */
    @JsonProperty("output_tokens")
    private Integer outputTokens;

    /**
     * 总Token数
     */
    @JsonProperty("total_tokens")
    private Integer totalTokens;
}
