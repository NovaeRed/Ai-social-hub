package cn.redture.aiEngine.pojo.dto;

import cn.redture.aiEngine.pojo.enums.AiPersonaTaskType;
import lombok.Data;

/**
 * 表示与用户 AI 画像相关的异步任务。
 */
@Data
public class AiPersonaTaskDTO {

    /**
     * 任务类型，例如：AI_PERSONA_INIT, AI_PERSONA_AUTH_DISABLED
     */
    private AiPersonaTaskType type;

    /**
     * 内部用户ID
     */
    private Long userId;
}
