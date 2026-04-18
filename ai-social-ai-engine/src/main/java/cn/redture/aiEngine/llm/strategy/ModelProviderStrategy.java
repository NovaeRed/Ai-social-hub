package cn.redture.aiEngine.llm.strategy;

import cn.redture.aiEngine.llm.core.execution.ModelExecutionContext;
import reactor.core.publisher.Flux;

/**
 * 模型供应商统一策略接口。
 */
public interface ModelProviderStrategy {

    /**
     * @return 供应商编码
     */
    String providerCode();

    /**
     * 流式调用。
     */
    Flux<String> stream(String prompt, ModelExecutionContext context);

    /**
     * 同步调用。
     */
    String call(String prompt, ModelExecutionContext context);

    /**
     * 同步工具调用。
     */
    String callWithTools(String prompt, ModelExecutionContext context);
}
