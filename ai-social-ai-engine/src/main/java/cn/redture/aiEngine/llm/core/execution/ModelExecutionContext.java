package cn.redture.aiEngine.llm.core.execution;

import cn.redture.aiEngine.llm.core.routing.ModelRouteDecision;
import cn.redture.aiEngine.llm.util.ModelProviderUtil;

/**
 * 统一模型执行上下文。
 */
public record ModelExecutionContext(String provider,
                                    String modelName) {

    public static ModelExecutionContext fromRoute(ModelRouteDecision route) {
        if (route == null) {
            return null;
        }
        return new ModelExecutionContext(
                ModelProviderUtil.normalizeProvider(route.resolvedProvider()),
                ModelProviderUtil.normalizeModelName(route.resolvedModelName())
        );
    }
}