package cn.redture.aiEngine.llm.core.execution;

import cn.redture.aiEngine.llm.core.routing.ModelRouteDecision;
import cn.redture.aiEngine.llm.factory.ModelProviderStrategyFactory;
import cn.redture.aiEngine.llm.strategy.ModelProviderStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 模型厂商执行器
 *
 * <p>将业务侧的模型路由决策映射到具体供应商调用</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelProviderExecutor {

    private final ModelProviderStrategyFactory modelProviderStrategyFactory;

    public Flux<String> stream(ModelRouteDecision route, String prompt) {
        ModelProviderStrategy strategy = modelProviderStrategyFactory.getProviderStrategy(route.resolvedProvider());
        return strategy.stream(prompt, route.resolvedModelName())
                .doOnError(error -> log.error("Error in stream task", error))
                .doOnComplete(() -> log.debug("Stream task completed"));
    }

    public String call(ModelRouteDecision route, String prompt) {
        ModelProviderStrategy strategy = modelProviderStrategyFactory.getProviderStrategy(route.resolvedProvider());
        return strategy.call(prompt, route.resolvedModelName());
    }

    public String callWithTools(ModelRouteDecision route, String prompt) {
        ModelProviderStrategy strategy = modelProviderStrategyFactory.getProviderStrategy(route.resolvedProvider());
        return strategy.callWithTools(prompt, route.resolvedModelName());
    }
}
