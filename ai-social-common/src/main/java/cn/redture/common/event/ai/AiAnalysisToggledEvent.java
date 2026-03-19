package cn.redture.common.event.ai;

/**
 * AI 画像授权状态变更事件。
 */
public record AiAnalysisToggledEvent(Long userId, boolean enabled) {
}
