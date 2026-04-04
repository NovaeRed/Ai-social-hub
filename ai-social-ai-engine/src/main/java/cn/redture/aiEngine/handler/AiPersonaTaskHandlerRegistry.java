package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.dto.AiAsyncTaskDTO;
import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;
import cn.redture.common.event.internal.AiAsyncTaskEvent;
import cn.redture.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 画像任务 handler 注册表：根据任务类型分发到对应的处理器。
 */
@Slf4j
@Component
public class AiPersonaTaskHandlerRegistry {

    private final Map<String, AiPersonaTaskHandler> handlerMap;

    @Autowired
    public AiPersonaTaskHandlerRegistry(Map<String, AiPersonaTaskHandler> handlerMap) {
        this.handlerMap = handlerMap;
    }

    @EventListener(condition = "#a0.domain == 'PERSONA_TASK'")
    public void onAiAsyncTaskEvent(AiAsyncTaskEvent event) {
        AiAsyncTaskDTO taskDTO = JsonUtil.fromJson(
            event.getTaskJsonPayload(), AiAsyncTaskDTO.class
        );
        AiPersonaTaskDTO task = new AiPersonaTaskDTO();
        if (taskDTO != null && taskDTO.getTaskType() != null) {
            String typeStr = taskDTO.getTaskType();
            if ("PERSONA_ANALYSIS".equals(typeStr)) {
                typeStr = "AI_PERSONA_ANALYSIS";
            }
            task.setTaskType(cn.redture.aiEngine.pojo.enums.AiPersonaTaskType.valueOf(typeStr));
            task.setUserId(taskDTO.getUserId());
        }
        dispatch(task);
    }

    public void dispatch(AiPersonaTaskDTO task) {
        if (task == null || task.getTaskType() == null) {
            log.warn("[AiPersonaTaskHandlerRegistry] 收到空任务或无类型任务，直接丢弃");
            return;
        }
        AiPersonaTaskHandler handler = handlerMap.get(task.getTaskType().toString());
        if (handler == null) {
            log.warn("[AiPersonaTaskHandlerRegistry] 未找到类型为 {} 的 handler，忽略此任务: {}", task.getTaskType(), task);
            return;
        }
        try {
            handler.handle(task);
        } catch (Exception e) {
            log.error("[AiPersonaTaskHandlerRegistry] 处理任务 {} 时发生异常", task, e);
        }
    }
}
