package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.service.AiInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 处理首次画像初始化任务（AI_PERSONA_INIT）。
 */
@Slf4j
@Component("AI_PERSONA_INIT")
@RequiredArgsConstructor
public class InitPersonaTaskHandler implements AiPersonaTaskHandler {

    private final AiInteractionService aiInteractionService;

    @Override
    public void handle(AiPersonaTaskDTO task) {
        if (task == null || task.getUserId() == null) {
            return;
        }
        log.info("[AI_PERSONA_INIT] 为用户 {} 执行首次画像初始化", task.getUserId());
        try {
            aiInteractionService.initPersona(task.getUserId());
        } catch (Exception e) {
            log.error("[AI_PERSONA_INIT] 用户 {} 画像初始化失败", task.getUserId(), e);
        }
    }
}
