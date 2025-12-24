package cn.redture.aiEngine.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * AI使用统计实体
 */
@Data
@TableName(value = "ai_usage_stats")
public class AiUsageStats {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private Long taskId;
    
    private String provider;
    
    private String modelName;
    
    private Integer tokensUsed;
    
    private Integer inputTokens;
    
    private Integer outputTokens;
    
    private BigDecimal cost;
    
    private LocalDate date;
    
    private OffsetDateTime createdAt;
}
