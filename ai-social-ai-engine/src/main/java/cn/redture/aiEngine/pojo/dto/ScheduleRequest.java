package cn.redture.aiEngine.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * 需要纳入日程识别的聊天附件。只接受文件公开 ID，实际读取仍会按当前用户授权校验。
     */
    @JsonProperty("file_public_ids")
    private List<String> filePublicIds;
}
