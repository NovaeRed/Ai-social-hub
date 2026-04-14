package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.dto.AiAsyncTaskDTO;
import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.pojo.enums.AiPersonaTaskType;
import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import cn.redture.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 画像任务 handler 注册表：根据任务类型分发到对应的处理器。
 */
@Slf4j
@Component
public class AiPersonaTaskHandlerRegistry implements AiTaskHandler {

    private final Map<String, AiPersonaTaskHandler> handlerMap;

    @Autowired
    public AiPersonaTaskHandlerRegistry(Map<String, AiPersonaTaskHandler> handlerMap) {
        this.handlerMap = handlerMap;
    }

    @Override
    public AsyncTaskDomain getDomain() {
        return AsyncTaskDomain.PERSONA_TASK;
    }

    @Override
    public void executeTask(String taskJson, Long userId, String eventType, String recordId) throws Exception {
        AiAsyncTaskDTO taskDTO = JsonUtil.fromJson(
            taskJson, AiAsyncTaskDTO.class
        );
        AiPersonaTaskDTO task = new AiPersonaTaskDTO();
        if (taskDTO != null && taskDTO.getTaskType() != null) {
            String typeStr = taskDTO.getTaskType();
            // TODO 语义统一
            if ("PERSONA_ANALYSIS".equals(typeStr)) {
                typeStr = "AI_PERSONA_ANALYSIS";
            }
            task.setTaskType(AiPersonaTaskType.valueOf(typeStr));
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
