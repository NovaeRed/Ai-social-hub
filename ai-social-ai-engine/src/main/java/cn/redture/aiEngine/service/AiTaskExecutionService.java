package cn.redture.aiEngine.service;

/**
 * AI 异步任务执行服务接口。
 * 负责消费队列任务后的执行与失败落库。
 */
public interface AiTaskExecutionService {

    /**
     * 执行统一异步队列中的 AI 任务。
     *
     * @param userId   用户 ID
     * @param aiTaskId AI 任务主键 ID
     */
    void executeQueuedAiTask(Long userId, Long aiTaskId);

    /**
     * 将统一异步队列中的 AI 任务标记为失败（对客户端可见）。
     *
     * @param userId       用户 ID
     * @param aiTaskId     AI 任务主键 ID
     * @param errorCode    失败错误码
     * @param errorMessage 失败错误信息
     */
    void failQueuedAiTask(Long userId, Long aiTaskId, String errorCode, String errorMessage);
}
