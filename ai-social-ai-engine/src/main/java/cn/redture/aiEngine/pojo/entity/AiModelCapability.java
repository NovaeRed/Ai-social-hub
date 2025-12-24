package cn.redture.aiEngine.pojo.entity;

import cn.redture.aiEngine.pojo.enums.AiTaskType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AI模型能力实体
 */
@Data
@TableName(value = "ai_model_capabilities")
public class AiModelCapability {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String modelName;
    
    private String provider;
    
    private AiTaskType capabilityType;
    
    private Boolean isEnabled;
    
    private Integer maxTokens;
    
    private BigDecimal inputPricePerMillion;
    
    private BigDecimal outputPricePerMillion;
    
    private OffsetDateTime createdAt;
}
