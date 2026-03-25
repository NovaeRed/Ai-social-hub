package cn.redture.aiEngine.service;

/**
 * AI 异步任务提交服务接口。
 * 负责将业务触发事件转换为异步任务并入队。
 */
public interface AiAsyncSubmissionService {

    /**
     * 基于时间线自动触发画像分析。
     *
     * @param userId 用户 ID
     */
    void analyzePersonaFromTimeline(Long userId);
}
