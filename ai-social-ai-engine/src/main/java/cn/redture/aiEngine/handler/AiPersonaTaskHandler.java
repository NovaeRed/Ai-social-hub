package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;

/**
 * AI 画像任务通用处理接口（策略）。
 */
public interface AiPersonaTaskHandler {

    /**
     * 处理一个 AI 画像相关任务。
     *
     * @param task 任务 DTO
     */
    void handle(AiPersonaTaskDTO task);
}
