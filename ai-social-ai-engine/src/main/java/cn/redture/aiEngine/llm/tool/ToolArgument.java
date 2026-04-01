package cn.redture.aiEngine.llm.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 工具的入参元数据定义。
 * 这个类对应大模型侧 Function Calling 需要的 Parameter 对象规范。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolArgument {

    /**
     * 参数名
     */
    private String name;
    
    /**
     * 参数类型（例如: string, integer, boolean, object, array）
     */
    private String type; 
    
    /**
     * 该参数的具体作用描述，供大模型决定是否采用此参数。
     */
    private String description;
    
    /**
     * 该参数是否必填
     */
    private boolean required;
}