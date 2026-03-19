package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class AiUsageVO {
    private UsageSummaryVO summary;
    
    @JsonProperty("daily_breakdown")
    private List<DailyUsageVO> dailyBreakdown;
    
    @JsonProperty("by_provider")
    private List<ProviderUsageVO> byProvider;
}
