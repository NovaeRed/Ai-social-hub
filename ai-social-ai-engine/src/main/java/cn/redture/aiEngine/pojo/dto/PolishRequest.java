package cn.redture.aiEngine.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文本润色请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PolishRequest extends BaseAiTaskRequest {
    
    /**
     * 待润色的消息内容
     */
    private String message;
}
