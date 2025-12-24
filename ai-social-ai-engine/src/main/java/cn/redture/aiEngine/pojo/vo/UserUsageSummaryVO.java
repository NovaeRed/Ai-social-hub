package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUsageSummaryVO {
    @JsonProperty("monthly_tokens")
    private Long monthlyTokens;
    
    @JsonProperty("monthly_cost")
    private BigDecimal monthlyCost;
}
