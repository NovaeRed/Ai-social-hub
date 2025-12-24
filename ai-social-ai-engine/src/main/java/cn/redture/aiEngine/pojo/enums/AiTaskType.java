package cn.redture.aiEngine.pojo.enums;

import cn.redture.common.annotation.PgEnum;

/**
 * AI任务类型枚举
 */
@PgEnum("ai_task_type_enum")
public enum AiTaskType {
    /**
     * 文本润色
     */
    POLISH,

    /**
     * 日程提取
     */
    SCHEDULE_EXTRACTION,

    /**
     * 性格分析
     */
    PERSONA_ANALYSIS,

    /**
     * 语音转文字
     */
    SPEECH_TO_TEXT,

    /**
     * 聊天总结
     */
    CHAT_SUMMARY,

    /**
     * 智能回复
     */
    SMART_REPLY,

    /**
     * 翻译
     */
    TRANSLATION
}
