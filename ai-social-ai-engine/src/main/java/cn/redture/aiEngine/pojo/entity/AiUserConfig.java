package cn.redture.aiEngine.pojo.entity;

import cn.redture.aiEngine.handler.JsonbTypeHandler;
import cn.redture.aiEngine.pojo.model.AiConfigParams;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * AI用户配置实体
 */
@Data
@TableName(value = "ai_user_configs", autoResultMap = true)
public class AiUserConfig {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    /**
     * 默认模型
     */
    private String defaultModel;
    
    /**
     * 默认提供商
     */
    private String defaultProvider;
    
    /**
     * 配置参数（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private AiConfigParams configParams;
    
    /**
     * 加密存储的API密钥（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, String> apiKeysEncrypted;
    
    /**
     * 是否激活
     */
    private Boolean isActive;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;
}
