package cn.redture.aiEngine.pojo.dto;

import lombok.Data;

/**
 * 画像异步任务载荷（PERSONA_TASK 领域，用于 ai_tasks 执行链）。
 */
@Data
public class AiAsyncTaskDTO {

    private Long userId;

    private Long aiTaskId;

    private String taskType;
}
