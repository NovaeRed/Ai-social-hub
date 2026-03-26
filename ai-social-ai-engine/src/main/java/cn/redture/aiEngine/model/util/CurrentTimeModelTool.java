package cn.redture.aiEngine.model.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 获取当前时间工具。
 */
@Component
public class CurrentTimeModelTool implements ModelTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public Map<String, Object> definition() {
        Map<String, Object> timezoneProperty = new LinkedHashMap<>();
        timezoneProperty.put("type", "string");
        timezoneProperty.put("description", "IANA 时区，例如 Asia/Shanghai，默认 Asia/Shanghai");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("timezone", timezoneProperty);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name());
        function.put("description", "获取当前时间，用于解析明天、下周等相对时间");
        function.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    @Override
    public String execute(String arguments) {
        String timezone = "Asia/Shanghai";
        try {
            JsonNode args = objectMapper.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
            String inputZone = args.path("timezone").asText("").trim();
            if (!inputZone.isEmpty()) {
                timezone = inputZone;
            }
        } catch (Exception ignored) {
            // ignore malformed arguments.
        }

        try {
            ZoneId zone = ZoneId.of(timezone);
            ZonedDateTime now = ZonedDateTime.now(zone);
            return "{\"timezone\":\"" + zone + "\",\"current_time\":\""
                    + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + "\",\"epoch_millis\":" + now.toInstant().toEpochMilli() + "}";
        } catch (Exception e) {
            return "{\"error\":\"invalid timezone\",\"timezone\":\"" + timezone + "\"}";
        }
    }
}
