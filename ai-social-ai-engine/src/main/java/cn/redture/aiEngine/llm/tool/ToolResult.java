package cn.redture.aiEngine.llm.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一的 AI 工具执行结果包装。
 * 携带执行状态和上下文数据以回送给大模型进行后续的思维链（CoT）推理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /**
     * 工具是否执行成功
     */
    private boolean success;

    /**
     * 工具返回给大模型的消息说明，或者执行失败时的异常信息
     */
    private String message;

    /**
     * 携带的具体 Payload 数据（如查询出的 JSON 列表等）
     */
    private Object data;

    /**
     * 构建成功结果
     * @param data 返回的数据对象
     * @return 工具结果包装
     */
    public static ToolResult success(Object data) {
        return ToolResult.builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    /**
     * 构建失败结果
     * @param message 失败说明给 LLM
     * @return 工具结果包装
     */
    public static ToolResult failure(String message) {
        return ToolResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}