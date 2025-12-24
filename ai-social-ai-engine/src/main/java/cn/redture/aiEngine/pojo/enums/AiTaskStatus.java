package cn.redture.aiEngine.pojo.enums;

import cn.redture.common.annotation.PgEnum;

/**
 * AI任务状态枚举
 */
@PgEnum("ai_task_status_enum")
public enum AiTaskStatus {
    /**
     * 待处理
     */
    PENDING,
    
    /**
     * 处理中
     */
    PROCESSING,
    
    /**
     * 已完成
     */
    COMPLETED,
    
    /**
     * 失败
     */
    FAILED,
    
    /**
     * 已取消
     */
    CANCELLED
}
