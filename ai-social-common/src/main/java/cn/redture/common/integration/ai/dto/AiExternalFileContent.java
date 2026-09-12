package cn.redture.common.integration.ai.dto;

/**
 * 已完成授权和大小限制的聊天附件文本。
 */
public record AiExternalFileContent(
        String filePublicId,
        String filename,
        String contentType,
        String text,
        boolean truncated
) {
}
