package cn.redture.aiEngine.model.strategy;

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
    Flux<String> stream(String prompt, String modelName);

    /**
     * 同步调用。
     */
    String call(String prompt, String modelName);

    /**
     * 同步工具调用。
     */
    String callWithTools(String prompt, String modelName);
}
