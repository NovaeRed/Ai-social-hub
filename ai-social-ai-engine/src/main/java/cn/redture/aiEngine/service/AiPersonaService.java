package cn.redture.aiEngine.service;

/**
 * 与用户 AI 画像（persona）相关的服务接口。
 */
public interface AiPersonaService {

    /**
     * 当用户的 AI 分析授权开关发生变化时触发。
     *
     * @param userId          内部用户ID
     * @param enabled         新的授权状态
     */
    void onAiAnalysisToggled(Long userId, boolean enabled);

    /**
     * 清除指定用户的 AI 画像（user_ai_contexts & user_ai_vectors 等）及相关缓存。
     *
     * @param userId 内部用户ID
     */
    void clearPersonaByUserId(Long userId);
}
