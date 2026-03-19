package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyUsageVO {
    private LocalDate date;
    
    @JsonProperty("tokens_used")
    private Long tokensUsed;
    
    private BigDecimal cost;
}
