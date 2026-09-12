package cn.redture.aiEngine.llm.tool;

import cn.redture.aiEngine.pojo.enums.AiTaskType;

/**
 * 由服务端创建的工具执行边界。模型提供的参数不能覆盖其中的身份或任务范围。
 */
public record ToolExecutionContext(
        Long userId,
        AiTaskType taskType,
        String conversationPublicId,
        String requestId
) {
    public ToolExecutionContext {
        if (userId == null) {
            throw new IllegalArgumentException("tool execution requires a user id");
        }
        if (taskType == null) {
            throw new IllegalArgumentException("tool execution requires a task type");
        }
    }
}
