package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.service.AiInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 画像分析处理器，负责处理 AI 画像分析任务。
 */
@Slf4j
@Component("AI_PERSONA_ANALYSIS")
@RequiredArgsConstructor
public class AiPersonaAnalysisHandler implements AiPersonaTaskHandler {

    private final AiInteractionService aiInteractionService;


    @Override
    public void handle(AiPersonaTaskDTO task) {
        if (task == null || task.getUserId() == null) {
            return;
        }
        log.info("[AI_PERSONA_ANALYSIS] 为用户 {} 执行自动画像分析", task.getUserId());
        try {
            aiInteractionService.analyzePersonaFromTimeline(task.getUserId());
        } catch (Exception e) {
            log.error("[AI_PERSONA_ANALYSIS] 用户 {} 自动画像分析失败", task.getUserId(), e);
        }

    }
}
