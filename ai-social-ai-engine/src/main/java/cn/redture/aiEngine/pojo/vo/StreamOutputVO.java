package cn.redture.aiEngine.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式输出响应VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamOutputVO {
    
    /**
     * 输出内容片段
     */
    private String output;
}
