package cn.redture.aiEngine.persona.handler.impl;

import cn.redture.aiEngine.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.persona.handler.AiPersonaTaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 处理关闭 AI 画像授权任务（AI_PERSONA_AUTH_DISABLED）。
 * 当前实现仅做占位日志，后续可扩展更多善后逻辑。
 */
@Slf4j
@Component("AI_PERSONA_AUTH_DISABLED")
public class AuthDisabledTaskHandler implements AiPersonaTaskHandler {

    @Override
    public void handle(AiPersonaTaskDTO task) {
        if (task == null) {
            return;
        }
        log.info("[AI_PERSONA_AUTH_DISABLED] 用户 {} 关闭 AI 画像授权（示例占位）", task.getUserId());
        // TODO: 停止后续画像更新
    }
}
