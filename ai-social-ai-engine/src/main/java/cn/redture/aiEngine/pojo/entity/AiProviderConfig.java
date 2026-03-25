package cn.redture.aiEngine.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * AI提供商配置实体
 */
@Data
@TableName(value = "ai_provider_configs", autoResultMap = true)
public class AiProviderConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String providerName;
    
    private String displayName;
    
    private Boolean isEnabled;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;
}
