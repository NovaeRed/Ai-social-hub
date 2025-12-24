package cn.redture.aiEngine.pojo.entity;

import cn.redture.aiEngine.handler.JsonbTypeHandler;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.model.ModelConfig;
import cn.redture.aiEngine.pojo.model.TokenUsage;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * AI任务实体
 */
@Data
@TableName(value = "ai_tasks", autoResultMap = true)
public class AiTask {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String publicId;
    
    private Long userId;
    
    private AiTaskType taskType;
    
    private AiTaskStatus taskStatus;
    
    private Long sourceMessageId;
    
    /**
     * 输入参数（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> inputPayload;
    
    /**
     * 输出结果（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> outputPayload;
    
    private String errorMessage;
    
    /**
     * 模型配置（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private ModelConfig modelConfig;
    
    /**
     * AI服务提供商
     */
    private String provider;
    
    /**
     * Token使用统计（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private TokenUsage tokenUsage;
    
    /**
     * 执行成本
     */
    private BigDecimal cost;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime startedAt;
    
    private OffsetDateTime completedAt;
}
