package cn.redture.aiEngine.llm.core.routing;

/**
 * 模型路由决策结果。
 */
public record ModelRouteDecision(String requestedModelOptionCode,
                                 String resolvedProvider,
                                 String resolvedModelName) {
}
