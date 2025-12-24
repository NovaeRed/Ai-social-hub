package cn.redture.aiEngine.mapper;

import cn.redture.aiEngine.pojo.entity.AiUsageStats;
import cn.redture.aiEngine.pojo.vo.DailyUsageVO;
import cn.redture.aiEngine.pojo.vo.ProviderUsageVO;
import cn.redture.aiEngine.pojo.vo.UsageSummaryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * AI使用统计 Mapper
 */
@Mapper
public interface AiUsageStatsMapper extends BaseMapper<AiUsageStats> {

    @Select("SELECT COALESCE(SUM(tokens_used), 0) as totalTokens, " +
            "COALESCE(SUM(input_tokens), 0) as inputTokens, " +
            "COALESCE(SUM(output_tokens), 0) as outputTokens, " +
            "COALESCE(SUM(cost), 0) as totalCost " +
            "FROM ai_usage_stats " +
            "WHERE user_id = #{userId} AND date >= #{startDate}")
    UsageSummaryVO getUsageSummary(@Param("userId") Long userId, @Param("startDate") LocalDate startDate);

    @Select("SELECT date, SUM(tokens_used) as tokensUsed, SUM(cost) as cost " +
            "FROM ai_usage_stats " +
            "WHERE user_id = #{userId} AND date BETWEEN #{dateFrom} AND #{dateTo} " +
            "GROUP BY date ORDER BY date")
    List<DailyUsageVO> getDailyBreakdown(@Param("userId") Long userId, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);

    @Select("SELECT provider, SUM(tokens_used) as tokensUsed, SUM(cost) as cost " +
            "FROM ai_usage_stats " +
            "WHERE user_id = #{userId} AND date BETWEEN #{dateFrom} AND #{dateTo} " +
            "GROUP BY provider")
    List<ProviderUsageVO> getUsageByProvider(@Param("userId") Long userId, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
