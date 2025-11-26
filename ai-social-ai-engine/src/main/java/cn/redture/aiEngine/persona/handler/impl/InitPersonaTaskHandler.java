package cn.redture.aiEngine.persona.handler.impl;

import cn.redture.aiEngine.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.persona.handler.AiPersonaTaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 处理首次画像初始化任务（AI_PERSONA_INIT）。
 * 当前实现仅做占位日志，后续可接入实际画像分析逻辑。
 */
@Slf4j
@Component("AI_PERSONA_INIT")
public class InitPersonaTaskHandler implements AiPersonaTaskHandler {

    @Override
    public void handle(AiPersonaTaskDTO task) {
        if (task == null) {
            return;
        }
        log.info("[AI_PERSONA_INIT] 为用户 {} 执行首次画像初始化（示例占位）", task.getUserId());
        // TODO: 查询最近聊天记录 / 行为数据，生成 user_ai_contexts & user_ai_vectors
    }
}
