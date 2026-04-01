package cn.redture.aiEngine.facade.orchestrator;

import cn.redture.aiEngine.llm.core.routing.ModelRouteDecision;
import cn.redture.aiEngine.mapper.AiTaskMapper;
import cn.redture.aiEngine.pojo.entity.AiTask;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.model.ModelConfig;
import cn.redture.aiEngine.pojo.vo.StreamOutputVO;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.exception.BaseException;
import cn.redture.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskOrchestrator {

    private final AiTaskService aiTaskService;
    private final AiTaskMapper aiTaskMapper;
    private final AiFacadeHandler aiFacadeHandler;

    public Flux<StreamOutputVO> submitAndExecuteStream(Long userId, AiTaskType taskType, Map<String, Object> params, String resultKey) {
        return Flux.defer(() -> {
            ModelRouteDecision route = aiFacadeHandler.resolveModelRoute(taskType, params);
            attachRequestedModelOptionCode(params, route);

            AiTask task = aiTaskService.createTask(userId, taskType, params);
            persistTaskRouting(task.getId(), params, route);

            StringBuilder fullContent = new StringBuilder();

            return aiFacadeHandler.executeTaskStream(userId, taskType, params, route)
                    .map(chunk -> {
                        fullContent.append(chunk);
                        return new StreamOutputVO(chunk);
                    })
                    .doOnSubscribe(subscription -> {
                        aiTaskService.updateTaskStatus(task.getId(), AiTaskStatus.PROCESSING);
                    })
                    .doOnComplete(() -> {
                        try {
                            Map<String, Object> resultData = new HashMap<>();
                            resultData.put(resultKey, fullContent.toString());
                            aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.COMPLETED, resultData, null);
                        } catch (Exception e) {
                            log.error("Failed to update task result", e);
                        }
                    })
                    .doOnError(e -> {
                        log.error("AI 任务执行失败: taskType={}, taskId={}", taskType, task.getId(), e);
                        try {
                             aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.FAILED, null, e.getMessage());
                        } catch (Exception ex) {
                             log.error("Failed to update task status to FAILED", ex);
                        }
                    });
        });
    }

    public <T> T submitAndExecuteSync(Long userId, AiTaskType taskType, Map<String, Object> params, Function<String, T> resultParser) {
        ModelRouteDecision route = aiFacadeHandler.resolveModelRoute(taskType, params);
        attachRequestedModelOptionCode(params, route);

        AiTask task = aiTaskService.createTask(userId, taskType, params);
        persistTaskRouting(task.getId(), params, route);

        try {
            aiTaskService.updateTaskStatus(task.getId(), AiTaskStatus.PROCESSING);
            String rawResult = aiFacadeHandler.executeTaskWithTools(userId, taskType, params, route);
            T parsedVo = resultParser.apply(rawResult);

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("raw_result", rawResult);
            resultData.put("result_parsed", parsedVo);

            aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.COMPLETED, resultData, null);
            return parsedVo;
        } catch (Exception e) {
            log.error("Sync AI task execution failed: taskType={}, taskId={}", taskType, task.getId(), e);
            aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.FAILED, null, e.getMessage());
            throw new BaseException(HttpStatus.INTERNAL_SERVER_ERROR, "任务执行失败: " + e.getMessage(), "TASK_EXECUTION_FAILED");
        }
    }

    private void persistTaskRouting(Long taskId, Map<String, Object> inputPayload, ModelRouteDecision route) {
        if (taskId == null || route == null) {
            return;
        }
        AiTask patch = new AiTask();
        patch.setId(taskId);
        patch.setInputPayload(inputPayload);
        patch.setProvider(route.resolvedProvider());
        patch.setModelConfig(new ModelConfig(route.resolvedModelName(), null, null, null, null, null));
        aiTaskMapper.updateById(patch);
    }

    private void attachRequestedModelOptionCode(Map<String, Object> params, ModelRouteDecision route) {
        if (params == null || route == null) {
            return;
        }
        Object value = params.get("model_option_code");
        if (value == null || String.valueOf(value).isBlank()) {
            params.put("model_option_code", route.requestedModelOptionCode());
        }
    }
}