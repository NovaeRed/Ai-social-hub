package cn.redture.aiEngine.pojo.dto;

import cn.redture.aiEngine.pojo.model.ModelConfig;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * AI任务响应
 */
@Data
public class AiTaskResponse {
    
    private String publicId;
    
    private String type;
    
    private String status;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime completedAt;
    
    private Map<String, Object> result;
    
    private ModelConfig modelConfig;
    
    private String errorMessage;
}
