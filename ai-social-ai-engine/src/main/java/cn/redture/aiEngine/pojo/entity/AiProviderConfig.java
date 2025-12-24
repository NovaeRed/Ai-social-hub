package cn.redture.aiEngine.pojo.entity;

import cn.redture.aiEngine.handler.JsonbTypeHandler;
import cn.redture.aiEngine.pojo.model.ModelConfig;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    
    private String apiBaseUrl;
    
    private Boolean isEnabled;
    
    /**
     * 默认配置参数（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private ModelConfig defaultConfig;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;
}
