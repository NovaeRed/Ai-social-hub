package cn.redture.aiEngine.model.util;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型工具注册中心。
 */
@Component
public class ModelToolRegistry {

    private final Map<String, ModelTool> tools;

    public ModelToolRegistry(List<ModelTool> toolList) {
        Map<String, ModelTool> map = new LinkedHashMap<>();
        for (ModelTool tool : toolList) {
            map.put(tool.name(), tool);
        }
        this.tools = Map.copyOf(map);
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }

    public List<Map<String, Object>> listToolDefinitions() {
        return tools.values().stream().map(ModelTool::definition).toList();
    }

    public String executeTool(String toolName, String arguments) {
        ModelTool tool = tools.get(toolName);
        if (tool == null) {
            return "{\"error\":\"unsupported tool: " + toolName + "\"}";
        }
        return tool.execute(arguments);
    }
}
