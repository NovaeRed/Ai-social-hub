package cn.redture.aiEngine.listener;

import cn.redture.aiEngine.service.AiConfigService;
import cn.redture.common.event.ai.AiAnalysisToggledEvent;
import cn.redture.common.event.ai.AiPersonaClearRequestedEvent;
import cn.redture.common.event.ai.UserMessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * AI 引擎领域事件监听器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiEngineDomainEventListener {

    private final AiConfigService aiConfigService;

    @EventListener
    public void onAiAnalysisToggled(AiAnalysisToggledEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        aiConfigService.onAiAnalysisToggled(event.userId(), event.enabled());
    }

    @EventListener
    public void onUserMessageCreated(UserMessageCreatedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        aiConfigService.onUserMessageCreated(event.userId(), event.messageTime());
    }

    @EventListener
    public void onAiPersonaClearRequested(AiPersonaClearRequestedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        aiConfigService.clearPersonaByUserIdAsync(event.userId());
    }
}
