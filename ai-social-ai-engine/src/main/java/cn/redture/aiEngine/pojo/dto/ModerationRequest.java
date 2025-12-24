package cn.redture.aiEngine.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容审核请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ModerationRequest extends BaseAiTaskRequest {
    
    /**
     * 待审核内容
     */
    private String content;
}
