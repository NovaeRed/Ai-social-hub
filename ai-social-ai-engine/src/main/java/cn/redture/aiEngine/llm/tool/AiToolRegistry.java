package cn.redture.aiEngine.llm.tool;

import cn.redture.common.util.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型工具注册中心。
 */
@Component
public class AiToolRegistry {

    private final Map<String, AiTool> tools;

    public AiToolRegistry(List<AiTool> toolList) {
        Map<String, AiTool> map = new LinkedHashMap<>();
        for (AiTool tool : toolList) {
            map.put(tool.getName(), tool);
        }
        this.tools = Map.copyOf(map);
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }

    public List<Map<String, Object>> listToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (AiTool tool : tools.values()) {
            definitions.add(buildOpenAiDefinition(tool));
        }
        return definitions;
    }

    public String executeTool(String toolName, String argumentsJson) {
        AiTool tool = tools.get(toolName);
        if (tool == null) {
            return "{\"error\":\"unsupported tool: " + toolName + "\"}";
        }
        try {
            Map<String, Object> argsMap = JsonUtil.fromJson(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson, Map.class);
            ToolResult result = tool.execute(argsMap);
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            return "{\"error\":\"tool execution failed: " + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> buildOpenAiDefinition(AiTool tool) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> requiredParams = new ArrayList<>();

        for (ToolArgument arg : tool.getArguments()) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", arg.getType());
            property.put("description", arg.getDescription());
            properties.put(arg.getName(), property);
            if (arg.isRequired()) {
                requiredParams.add(arg.getName());
            }
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (!requiredParams.isEmpty()) {
            parameters.put("required", requiredParams);
        }

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.getName());
        function.put("description", tool.getDescription());
        function.put("parameters", parameters);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "function");
        root.put("function", function);
        return root;
    }
}
