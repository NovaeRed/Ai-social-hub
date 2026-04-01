package cn.redture.aiEngine.llm.tool;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 获取当前时间工具。
 */
@Component
public class CurrentTimeTool implements AiTool {

    @Override
    public String getName() {
        return "get_current_time";
    }

    @Override
    public String getDescription() {
        return "获取当前时间，用于解析明天、下周等相对时间";
    }

    @Override
    public List<ToolArgument> getArguments() {
        return List.of(
                ToolArgument.builder()
                        .name("timezone")
                        .type("string")
                        .description("IANA 时区，例如 Asia/Shanghai，默认 Asia/Shanghai")
                        .required(false)
                        .build()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String timezone = "Asia/Shanghai";
        try {
            if (arguments != null && arguments.containsKey("timezone")) {
                String inputZone = arguments.get("timezone").toString().trim();
                if (!inputZone.isEmpty()) {
                    timezone = inputZone;
                }
            }
        } catch (Exception ignored) {
            // ignore malformed arguments.
        }

        try {
            ZoneId zone = ZoneId.of(timezone);
            ZonedDateTime now = ZonedDateTime.now(zone);
            return ToolResult.success(Map.of(
                    "timezone", zone.toString(),
                    "current_time", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    "epoch_millis", now.toInstant().toEpochMilli()
            ));
        } catch (Exception e) {
            return ToolResult.failure("invalid timezone: " + timezone);
        }
    }
}
