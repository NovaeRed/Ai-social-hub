package cn.redture.aiEngine.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * AI任务请求基类
 */
@Data
public class BaseAiTaskRequest {

    /**
     * 模型选项编码（可选），由服务端解析为托管配置。
     */
    @JsonProperty("model_option_code")
    private String modelOptionCode;
    
    /**
     * 上下文信息（可选）
     */
    private Map<String, Object> context;
}
