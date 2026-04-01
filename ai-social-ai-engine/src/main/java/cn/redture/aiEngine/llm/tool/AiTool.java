package cn.redture.aiEngine.llm.tool;

import java.util.List;
import java.util.Map;

/**
 * 统一的大模型工具接口。
 * 随着业务向 Agentic Native 演进，所有的对外可被大模型调用的 API/动作 都应实现此接口，以便 RAG 应用能通过此标准发现并调用工具。
 */
public interface AiTool {
    
    /**
     * @return 工具的名称，需保持在系统的工具集合中唯一（例如: "search_user_profile", "get_weather"）
     */
    String getName();
    
    /**
     * @return 给 LLM 看的工具描述，必须定义清楚该工具的功能、参数的要求及预期行为。
     */
    String getDescription();
    
    /**
     * 定义该工具需要的结构化入参。
     * @return 工具参数列表
     */
    List<ToolArgument> getArguments();
    
    /**
     * 执行具体工具逻辑。
     * @param arguments 大模型侧提取出的参数
     * @return 工具执行结果，会被统一序列化包装回送给大模型
     */
    ToolResult execute(Map<String, Object> arguments);
}