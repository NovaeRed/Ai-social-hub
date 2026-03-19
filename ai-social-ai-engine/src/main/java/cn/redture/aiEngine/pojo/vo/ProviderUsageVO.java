package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderUsageVO {
    private String provider;
    
    @JsonProperty("tokens_used")
    private Long tokensUsed;
    
    private BigDecimal cost;
}
