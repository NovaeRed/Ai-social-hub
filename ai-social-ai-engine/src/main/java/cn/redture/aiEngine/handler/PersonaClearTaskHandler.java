package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 处理手动清除 AI 画像任务（AI_PERSONA_CLEAR）。
 */
@Slf4j
@Component("AI_PERSONA_CLEAR")
@RequiredArgsConstructor
public class PersonaClearTaskHandler implements AiPersonaTaskHandler {

    private final AiConfigService aiConfigService;

    @Override
    public void handle(AiPersonaTaskDTO task) {
        if (task == null || task.getUserId() == null) {
            return;
        }
        log.info("[AI_PERSONA_CLEAR] 用户 {} 手动清除 AI 画像", task.getUserId());
        try {
            aiConfigService.clearPersonaByUserId(task.getUserId());
        } catch (Exception e) {
            log.error("[AI_PERSONA_CLEAR] 用户 {} 清除画像失败", task.getUserId(), e);
        }
    }
}
