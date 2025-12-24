package cn.redture.aiEngine.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.time.LocalDateTime;

/**
 * 获取当前时间的工具类
 */
@Slf4j
public class CurrentTimeTool {

    // 获取当前时间的工具方法
    // description: 工具的描述信息
    // returnDirect: 是否直接返回结果，false表示结果会被进一步处理
    @Tool(description = "获取当前时间", returnDirect = false)
    public String getCurrentTime() {
        log.debug("调用了获取当前时间的工具方法");
        return LocalDateTime.now().toString();
    }
}
