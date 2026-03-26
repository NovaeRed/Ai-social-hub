package cn.redture.aiEngine.model.util;

import java.util.Map;

/**
 * 模型工具抽象。
 */
public interface ModelTool {

    /**
     * 工具名称（唯一）。
     */
    String name();

    /**
     * OpenAI 兼容 tools 定义。
     */
    Map<String, Object> definition();

    /**
     * 执行工具并返回 JSON 字符串结果。
     */
    String execute(String arguments);
}
