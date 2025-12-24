package cn.redture.aiEngine.pojo.dto;

import lombok.Data;

/**
 * 消息对象
 */
@Data
public class MessageItem {
    private String sender;
    private String content;
    private String timestamp;
}