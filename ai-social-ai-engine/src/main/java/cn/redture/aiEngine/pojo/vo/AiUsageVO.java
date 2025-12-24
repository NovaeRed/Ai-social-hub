package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AiUsageVO {
    private UsageSummaryVO summary;
    
    @JsonProperty("daily_breakdown")
    private List<DailyUsageVO> dailyBreakdown;
    
    @JsonProperty("by_provider")
    private List<ProviderUsageVO> byProvider;
}
