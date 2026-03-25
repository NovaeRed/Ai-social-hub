package cn.redture.aiEngine.pojo.entity;

import cn.redture.aiEngine.pojo.enums.AiTaskType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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

    /**
     * 是否作为该能力的默认路由模型。
     */
    private Boolean isDefault;
    
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
