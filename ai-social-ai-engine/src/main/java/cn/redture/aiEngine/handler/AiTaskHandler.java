package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;

/**
 * AI异步任务处理策略接口。
 * 用于替代早期基于 Spring ApplicationEventPublisher 发布字符串/对象的内部黑盒路由，
 * 提升任务链路的可追溯性与拓展性。
 */
public interface AiTaskHandler {

    /**
     * @return 支持处理的任务领域
     */
    AsyncTaskDomain getDomain();

    /**
     * 执行具体任务逻辑
     *
     * @param taskJson  任务负载JSON
     * @param userId    用户ID (可能为空)
     * @param eventType 触发事件类型 (可能为空)
     * @param recordId  Redis Stream 记录ID
     * @throws Exception 处理异常，抛出异常后框架会记录错误并交由统一异常服务退避或进入DLQ
     */
    void executeTask(String taskJson, Long userId, String eventType, String recordId) throws Exception;
}