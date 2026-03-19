package cn.redture.common.integration.ai.dto;

import lombok.Data;

/**
 * AI 外部消息对象（来自聊天域）。
 */
@Data
public class AiExternalMessageItem {
    private String sender;
    private String content;
    private String timestamp;
}
