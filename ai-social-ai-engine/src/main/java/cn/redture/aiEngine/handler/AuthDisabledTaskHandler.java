package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.service.AiInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 处理关闭 AI 画像授权任务（AI_PERSONA_AUTH_DISABLED）。
 */
@Slf4j
@Component("AI_PERSONA_AUTH_DISABLED")
@RequiredArgsConstructor
public class AuthDisabledTaskHandler implements AiPersonaTaskHandler {

    private final AiInteractionService aiInteractionService;

    @Override
    public void handle(AiPersonaTaskDTO task) {
        if (task == null || task.getUserId() == null) {
            return;
        }
        log.info("[AI_PERSONA_AUTH_DISABLED] 用户 {} 关闭 AI 画像授权", task.getUserId());
        try {
            aiInteractionService.disablePersona(task.getUserId());
        } catch (Exception e) {
            log.error("[AI_PERSONA_AUTH_DISABLED] 用户 {} 画像禁用失败", task.getUserId(), e);
        }
    }
}
