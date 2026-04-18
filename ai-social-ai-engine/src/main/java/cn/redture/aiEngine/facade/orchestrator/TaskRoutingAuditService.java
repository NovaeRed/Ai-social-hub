package cn.redture.aiEngine.facade.orchestrator;

import cn.redture.aiEngine.llm.core.routing.ModelRouteDecision;
import cn.redture.aiEngine.mapper.AiTaskMapper;
import cn.redture.aiEngine.pojo.entity.AiTask;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.model.ModelConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 统一处理任务模型路由审计字段写入。
 */
@Component
@RequiredArgsConstructor
public class TaskRoutingAuditService {

    private final AiTaskMapper aiTaskMapper;

    /**
     * 确保任务输入中带有请求模型编码。
     *
     * @param params 任务输入参数
     * @param route 路由决策
     */
    public void ensureRequestedModelOptionCode(Map<String, Object> params, ModelRouteDecision route) {
        if (params == null || route == null) {
            return;
        }
        Object value = params.get("model_option_code");
        if (value == null || String.valueOf(value).isBlank()) {
            params.put("model_option_code", route.requestedModelOptionCode());
        }
    }

    /**
     * 持久化路由信息到任务记录。
     *
     * @param taskId 任务 ID
     * @param inputPayload 任务输入
     * @param route 路由决策
     */
    public void persistRouting(Long taskId, Map<String, Object> inputPayload, ModelRouteDecision route) {
        persist(taskId, inputPayload, route, false);
    }

    /**
     * 持久化路由信息并标记任务进入处理中状态。
     *
     * @param taskId 任务 ID
     * @param inputPayload 任务输入
     * @param route 路由决策
     */
    public void persistRoutingAndMarkProcessing(Long taskId,
                                                Map<String, Object> inputPayload,
                                                ModelRouteDecision route) {
        persist(taskId, inputPayload, route, true);
    }

    /**
     * 执行路由审计信息写入。
     *
     * @param taskId 任务 ID
     * @param inputPayload 任务输入
     * @param route 路由决策
     * @param markProcessing 是否同时标记处理状态
     */
    private void persist(Long taskId,
                         Map<String, Object> inputPayload,
                         ModelRouteDecision route,
                         boolean markProcessing) {
        if (taskId == null || route == null) {
            return;
        }
        AiTask patch = new AiTask();
        patch.setId(taskId);
        patch.setInputPayload(inputPayload);
        patch.setProvider(route.resolvedProvider());
        patch.setModelConfig(new ModelConfig(route.resolvedModelName(), null, null, null, null, null));
        if (markProcessing) {
            patch.setTaskStatus(AiTaskStatus.PROCESSING);
            patch.setStartedAt(OffsetDateTime.now());
        }
        aiTaskMapper.updateById(patch);
    }
}
