package cn.redture.aiEngine.util;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具注册类
 */
@Configuration
public class ToolRegister {

    @Bean
    public ToolCallback[] allTools() {
        CurrentTimeTool currentTimeTool = new CurrentTimeTool();
        return ToolCallbacks.from(
                currentTimeTool
        );
    }
}
