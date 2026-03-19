package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageSummaryVO {
    @JsonProperty("total_tokens")
    private Long totalTokens;
    
    @JsonProperty("input_tokens")
    private Long inputTokens;
    
    @JsonProperty("output_tokens")
    private Long outputTokens;
    
    @JsonProperty("total_cost")
    private BigDecimal totalCost;
    
    @JsonProperty("date_from")
    private String dateFrom;
    
    @JsonProperty("date_to")
    private String dateTo;
}
