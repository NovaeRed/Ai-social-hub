package cn.redture.aiEngine.llm.tool;

import cn.redture.common.util.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 模型工具注册中心。
 */
@Component
public class AiToolRegistry {

    private final Map<String, AiTool> tools;

    public AiToolRegistry(List<AiTool> toolList) {
        Map<String, AiTool> map = new LinkedHashMap<>();
        for (AiTool tool : toolList) {
            if (map.putIfAbsent(tool.getName(), tool) != null) {
                throw new IllegalStateException("duplicate AI tool name: " + tool.getName());
            }
        }
        this.tools = Map.copyOf(map);
    }

    public boolean hasTools(ToolExecutionContext context) {
        return !allowedTools(context).isEmpty();
    }

    public List<Map<String, Object>> listToolDefinitions(ToolExecutionContext context) {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (AiTool tool : allowedTools(context)) {
            definitions.add(buildOpenAiDefinition(tool));
        }
        return definitions;
    }

    public String executeTool(ToolExecutionContext context, String toolName, String argumentsJson) {
        AiTool tool = tools.get(toolName);
        if (tool == null || !isAllowed(tool, context)) {
            return "{\"error\":\"tool is not allowed for this task\"}";
        }
        try {
            Map<String, Object> argsMap = JsonUtil.fromJson(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson, Map.class);
            validateArguments(tool, argsMap);
            ToolResult result = tool.execute(context, argsMap);
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            return "{\"error\":\"tool execution failed: " + e.getMessage() + "\"}";
        }
    }

    private List<AiTool> allowedTools(ToolExecutionContext context) {
        return tools.values().stream().filter(tool -> isAllowed(tool, context)).toList();
    }

    private boolean isAllowed(AiTool tool, ToolExecutionContext context) {
        Set<cn.redture.aiEngine.pojo.enums.AiTaskType> taskTypes = tool.supportedTaskTypes();
        return context != null && taskTypes != null && taskTypes.contains(context.taskType());
    }

    /**
     * 在进入具体工具前拒绝模型额外字段及明显不匹配的参数类型。
     */
    private void validateArguments(AiTool tool, Map<String, Object> arguments) {
        Map<String, ToolArgument> definitions = tool.getArguments().stream()
                .collect(Collectors.toMap(ToolArgument::getName, item -> item));
        for (Map.Entry<String, Object> argument : arguments.entrySet()) {
            ToolArgument definition = definitions.get(argument.getKey());
            if (definition == null) {
                throw new IllegalArgumentException("unexpected tool argument: " + argument.getKey());
            }
            if (!matchesType(definition.getType(), argument.getValue())) {
                throw new IllegalArgumentException("invalid type for tool argument: " + argument.getKey());
            }
        }
        for (ToolArgument definition : definitions.values()) {
            if (definition.isRequired() && (arguments.get(definition.getName()) == null
                    || String.valueOf(arguments.get(definition.getName())).isBlank())) {
                throw new IllegalArgumentException("missing required tool argument: " + definition.getName());
            }
        }
    }

    private boolean matchesType(String type, Object value) {
        if (value == null) {
            return true;
        }
        return switch (type) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List<?>;
            case "object" -> value instanceof Map<?, ?>;
            default -> false;
        };
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
        parameters.put("additionalProperties", false);
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
