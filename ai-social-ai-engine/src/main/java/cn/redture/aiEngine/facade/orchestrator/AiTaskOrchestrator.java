package cn.redture.aiEngine.facade.orchestrator;

import cn.redture.aiEngine.llm.core.routing.ModelRouteDecision;
import cn.redture.aiEngine.pojo.entity.AiTask;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.vo.StreamOutputVO;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.aiEngine.service.agent.ConversationAgentMemoryService;
import cn.redture.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * AI 任务编排器。
 * <p>
 * 负责统一执行在线任务的路由决策、任务落库、状态流转与结果写回。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskOrchestrator {

    private final AiTaskService aiTaskService;
    private final AiFacadeHandler aiFacadeHandler;
    private final ConversationAgentMemoryService conversationAgentMemoryService;

    /**
     * 提交并执行流式 AI 任务。
     *
     * @param userId 用户 ID
     * @param taskType 任务类型
     * @param params 任务输入参数
     * @param resultKey 结果字段名
     * @return 流式输出片段
     */
    public Flux<StreamOutputVO> submitAndExecuteStream(Long userId, AiTaskType taskType, Map<String, Object> params, String resultKey) {
        return Flux.defer(() -> {
            ModelRouteDecision route = aiFacadeHandler.resolveModelRoute(taskType, params);
            ensureRequestedModelOptionCode(params, route);

            AiTask task = aiTaskService.createTaskAndMarkProcessing(userId, taskType, params, route.resolvedProvider(), route.resolvedModelName());

            StringBuilder fullContent = new StringBuilder();

            return aiFacadeHandler.executeTaskStream(userId, taskType, params, route)
                    .map(chunk -> {
                        fullContent.append(chunk);
                        return new StreamOutputVO(chunk);
                    })
                    .doOnComplete(() -> {
                        try {
                            Map<String, Object> resultData = new HashMap<>();
                            String finalOutput = fullContent.toString();
                            resultData.put(resultKey, finalOutput);
                            aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.COMPLETED, resultData, null);
                            updateConversationMemory(userId, taskType, params, finalOutput);
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
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 提交并执行同步 AI 任务。
     *
     * @param userId 用户 ID
     * @param taskType 任务类型
     * @param params 任务输入参数
     * @param resultParser 结果解析函数
     * @param <T> 解析后的结果类型
     * @return 解析后的结果对象
     */
    public <T> T submitAndExecuteSync(Long userId, AiTaskType taskType, Map<String, Object> params, Function<String, T> resultParser) {
        ModelRouteDecision route = aiFacadeHandler.resolveModelRoute(taskType, params);
        ensureRequestedModelOptionCode(params, route);

        AiTask task = aiTaskService.createTaskAndMarkProcessing(
                userId,
                taskType,
                params,
                route.resolvedProvider(),
                route.resolvedModelName()
        );

        try {
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

    private void ensureRequestedModelOptionCode(Map<String, Object> params, ModelRouteDecision route) {
        if (params == null || route == null) {
            return;
        }
        Object value = params.get("model_option_code");
        if (value == null || String.valueOf(value).isBlank()) {
            params.put("model_option_code", route.requestedModelOptionCode());
        }
    }

    private void updateConversationMemory(Long userId, AiTaskType taskType, Map<String, Object> params, String output) {
        if (taskType != AiTaskType.SMART_REPLY && taskType != AiTaskType.CHAT_SUMMARY) {
            return;
        }
        if (params == null) {
            return;
        }

        String conversationPublicId = stringValue(params.get("conversation_public_id"));
        if (conversationPublicId.isBlank()) {
            return;
        }

        Object history = params.get("conversation_history");
        String userInput = taskType == AiTaskType.SMART_REPLY
                ? stringValue(params.get("message"))
                : stringValue(params.get("content"));

        conversationAgentMemoryService.updateMemory(
                userId,
                conversationPublicId,
                history,
                userInput,
                output
        );
    }

    private String stringValue(Object source) {
        return source == null ? "" : String.valueOf(source);
    }
}