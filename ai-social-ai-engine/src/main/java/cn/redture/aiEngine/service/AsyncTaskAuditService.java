package cn.redture.aiEngine.service;

import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;

import java.util.Map;

/**
 * 异步任务审计与指标服务。
 */
public interface AsyncTaskAuditService {

    /**
     * 记录审计动作并累计指标。
     *
     * @param action   动作标识
     * @param domain   任务领域
     * @param metadata 结构化元数据
     */
    void record(String action, AsyncTaskDomain domain, Map<String, Object> metadata);
}
