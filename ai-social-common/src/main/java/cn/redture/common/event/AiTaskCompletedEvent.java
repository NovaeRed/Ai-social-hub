package cn.redture.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 任务完成事件实体载荷
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskCompletedEvent {
    private Long aiTaskId;

    private String taskPublicId;

    private String taskType;

    private String status;
}