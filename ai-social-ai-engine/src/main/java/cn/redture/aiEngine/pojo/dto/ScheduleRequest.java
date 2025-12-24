package cn.redture.aiEngine.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


/**
 * 日程提取请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleRequest extends BaseAiTaskRequest {

    /**
     * 消息列表
     */
    private List<MessageItem> messages;
}
