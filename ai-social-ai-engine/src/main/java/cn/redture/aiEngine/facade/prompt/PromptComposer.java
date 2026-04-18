package cn.redture.aiEngine.facade.prompt;

import cn.redture.aiEngine.pojo.enums.AiTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Prompt 组装流水线。
 */
@Component
@RequiredArgsConstructor
public class PromptComposer {

    private static final String SYSTEM_GUARDRAIL_KEY = "SYSTEM_GUARDRAIL";

    private final PromptTemplateRegistry promptTemplateRegistry;
    private final PromptContextFactory promptContextFactory;
    private final PromptInjectionRiskService promptInjectionRiskService;

    /**
     * 组装最终 Prompt。
     *
     * @param taskType 任务类型
     * @param params 任务参数
     * @return 最终 Prompt
     */
    public String compose(AiTaskType taskType, Map<String, Object> params) {
        if (taskType == AiTaskType.SPEECH_TO_TEXT) {
            return null;
        }

        PromptContextFactory.PromptBuildContext context = promptContextFactory.buildContext(taskType, params);
        promptInjectionRiskService.validate(taskType, context.riskInputs());

        String systemGuardrail = promptTemplateRegistry.getTemplate(SYSTEM_GUARDRAIL_KEY);
        String taskTemplate = promptTemplateRegistry.getTemplate(context.templateKey());
        String taskPrompt = promptTemplateRegistry.renderTemplate(taskTemplate, context.renderParams());

        if (systemGuardrail.isBlank()) {
            return taskPrompt;
        }
        if (taskPrompt == null || taskPrompt.isBlank()) {
            return systemGuardrail;
        }
        return systemGuardrail + "\n\n" + taskPrompt;
    }
}
