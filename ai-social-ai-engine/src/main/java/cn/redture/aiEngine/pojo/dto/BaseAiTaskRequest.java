package cn.redture.aiEngine.pojo.dto;

import cn.redture.aiEngine.pojo.model.ModelConfig;
import lombok.Data;

import java.util.Map;

/**
 * AI任务请求基类
 */
@Data
public class BaseAiTaskRequest {
    
    /**
     * 覆盖配置（可选）
     */
    private ModelConfig overrideConfig;
    
    /**
     * 上下文信息（可选）
     */
    private Map<String, Object> context;
}
