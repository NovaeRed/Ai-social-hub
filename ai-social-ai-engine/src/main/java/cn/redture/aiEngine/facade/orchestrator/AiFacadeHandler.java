package cn.redture.aiEngine.facade.orchestrator;

import cn.redture.aiEngine.facade.prompt.ModelOutputSecurityValidator;
import cn.redture.aiEngine.facade.prompt.PromptComposer;
import cn.redture.aiEngine.llm.core.execution.ModelExecutionContext;
import cn.redture.aiEngine.llm.factory.ModelProviderStrategyFactory;
import cn.redture.aiEngine.llm.core.routing.ModelSelector;
import cn.redture.aiEngine.llm.core.routing.ModelRouteDecision;
import cn.redture.aiEngine.llm.strategy.ModelProviderStrategy;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 门面处理器。
 * <p>
 * 负责组装 Prompt、解析路由并将调用分发到具体模型策略。
 * </p>
 */
@Slf4j
@Component
public class AiFacadeHandler {

    private final ModelSelector modelSelector;
    private final ModelProviderStrategyFactory modelProviderStrategyFactory;
    private final PromptComposer promptComposer;
    private final ModelOutputSecurityValidator modelOutputSecurityValidator;

    public AiFacadeHandler(ModelSelector modelSelector,
                           ModelProviderStrategyFactory modelProviderStrategyFactory,
                           PromptComposer promptComposer,
                           ModelOutputSecurityValidator modelOutputSecurityValidator) {
        this.modelSelector = modelSelector;
        this.modelProviderStrategyFactory = modelProviderStrategyFactory;
        this.promptComposer = promptComposer;
        this.modelOutputSecurityValidator = modelOutputSecurityValidator;
    }

    /**
     * 执行流式任务并返回模型输出流。
     *
     * @param userId 用户 ID
     * @param taskType 任务类型
     * @param params 任务参数
     * @param route 模型路由决策
     * @return 模型流式输出
     */
    public Flux<String> executeTaskStream(Long userId, AiTaskType taskType, Map<String, Object> params, ModelRouteDecision route) {
        DispatchTarget dispatch = resolveDispatchTarget(route, "stream");
        log.debug("Execute stream task: userId={}, taskType={}, provider={}, model={}", userId, taskType, dispatch.context().provider(), dispatch.context().modelName());
        String prompt = buildPrompt(taskType, params);
        StringBuilder outputBuffer = new StringBuilder();

        return dispatch.strategy().stream(prompt, dispatch.context())
            .<String>handle((chunk, sink) -> {
                    outputBuffer.append(chunk);
                    modelOutputSecurityValidator.validateChunk(outputBuffer);
                    sink.next(chunk);
                })
                .doOnError(error -> log.error("Error in stream task", error))
                .doOnComplete(() -> log.debug("Stream task completed"));
    }

    /**
     * 执行同步任务并返回文本结果。
     *
     * @param userId 用户 ID
     * @param taskType 任务类型
     * @param params 任务参数
     * @param route 模型路由决策
     * @return 同步文本结果
     */
    public String executeTask(Long userId, AiTaskType taskType, Map<String, Object> params, ModelRouteDecision route) {
        DispatchTarget dispatch = resolveDispatchTarget(route, "call");
        log.debug("Execute task: userId={}, taskType={}, provider={}, model={}", userId, taskType, dispatch.context().provider(), dispatch.context().modelName());
        String prompt = buildPrompt(taskType, params);
        String result = dispatch.strategy().call(prompt, dispatch.context());
        modelOutputSecurityValidator.validateFinal(result);
        return result;
    }

    /**
     * 执行带工具调用的同步任务。
     *
     * @param userId 用户 ID
     * @param taskType 任务类型
     * @param params 任务参数
     * @param route 模型路由决策
     * @return 模型返回结果
     */
    public String executeTaskWithTools(Long userId, AiTaskType taskType, Map<String, Object> params, ModelRouteDecision route) {
        DispatchTarget dispatch = resolveDispatchTarget(route, "callWithTools");
        log.debug("Execute task with tools: userId={}, taskType={}, provider={}, model={}", userId, taskType, dispatch.context().provider(), dispatch.context().modelName());
        String prompt = buildPrompt(taskType, params);
        String result = dispatch.strategy().callWithTools(prompt, dispatch.context());
        modelOutputSecurityValidator.validateFinal(result);
        return result;
    }

    /**
     * 将路由决策解析为可执行分发目标。
     *
     * @param route 模型路由决策
     * @param operation 操作名称（用于错误上下文）
     * @return 分发目标（执行上下文 + 策略实现）
     */
    private DispatchTarget resolveDispatchTarget(ModelRouteDecision route, String operation) {
        if (route == null) {
            throw new IllegalStateException("模型路由决策为空，无法执行操作: " + operation);
        }

        ModelExecutionContext context = ModelExecutionContext.fromRoute(route);
        if (context == null || context.provider() == null || context.provider().isBlank()
                || context.modelName() == null || context.modelName().isBlank()) {
            throw new IllegalStateException("模型执行上下文无效，无法执行操作: " + operation);
        }

        ModelProviderStrategy strategy = modelProviderStrategyFactory.getProviderStrategy(context.provider());
        return new DispatchTarget(context, strategy);
    }

    /**
     * 分发目标结构。
     *
     * @param context 执行上下文
     * @param strategy 供应商策略
     */
    private record DispatchTarget(ModelExecutionContext context, ModelProviderStrategy strategy) {
    }

    /**
     * 按请求参数解析模型路由。
     *
     * @param taskType 任务类型
     * @param params 输入参数
     * @return 路由决策
     */
    public ModelRouteDecision resolveModelRoute(AiTaskType taskType, Map<String, Object> params) {
        return modelSelector.resolveModelRoute(taskType, params);
    }

    /**
     * 解析系统默认模型路由。
     *
     * @param taskType 任务类型
     * @return 默认路由决策
     */
    public ModelRouteDecision resolveSystemDefaultRoute(AiTaskType taskType) {
        return modelSelector.resolveSystemDefaultRoute(taskType);
    }

    /**
     * 构建模型调用 Prompt。
     *
     * @param taskType 任务类型
     * @param params 原始参数
     * @return 渲染后的 Prompt 文本
     */
    public String buildPrompt(AiTaskType taskType, Map<String, Object> params) {
        return promptComposer.compose(taskType, params);
    }
}