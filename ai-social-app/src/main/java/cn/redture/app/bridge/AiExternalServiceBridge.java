package cn.redture.app.bridge;

import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.chat.pojo.entity.ChatFile;
import cn.redture.chat.service.ChatFileService;
import cn.redture.chat.service.MessageService;
import cn.redture.chat.sse.Notification;
import cn.redture.chat.sse.SseEmitterService;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.integration.ai.dto.AiExternalMessageItem;
import cn.redture.common.integration.ai.dto.AiExternalFileContent;
import cn.redture.common.integration.notification.NotificationExternalService;
import cn.redture.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.core.io.Resource;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 引擎调用的外部服务桥接实现，负责将 AI 引擎需要的数据请求转发到对应的服务，并将结果转换成 AI 引擎需要的格式。
 */
@Service
@RequiredArgsConstructor
public class AiExternalServiceBridge implements AiExternalService, NotificationExternalService {

    private static final long MAX_PARSE_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_EXTRACTED_TEXT_CHARS = 120_000;

    private final UserService userService;
    private final MessageService messageService;
    private final ChatFileService chatFileService;
    private final SseEmitterService sseEmitterService;

    @Override
    public boolean isAiAnalysisEnabled(Long userId) {
        return userService.isAiAnalysisEnabled(userId);
    }

    @Override
    public List<AiExternalMessageItem> getRecentContextMessages(String conversationPublicId, int limit) {
        List<MessageItemVO> messages = messageService.getRecentContextMessages(conversationPublicId, limit);
        return convert(messages, null);
    }

    @Override
    public List<AiExternalMessageItem> getMessagesByIds(List<Long> messageIds) {
        List<MessageItemVO> messages = messageService.getMessagesByIds(messageIds);
        return convert(messages, null);
    }

    @Override
    public List<AiExternalMessageItem> getUserMessagesInConversation(String conversationPublicId, Long userId, int limit) {
        List<MessageItemVO> messages = messageService.getUserMessagesInConversation(conversationPublicId, userId, limit);
        return convert(messages, userId);
    }

    @Override
    public List<AiExternalMessageItem> getUserRecentMessagesForAnalysis(Long userId, int limit) {
        List<MessageItemVO> messages = messageService.getUserRecentMessagesForAnalysis(userId, limit);
        return convert(messages, userId);
    }

    /**
     * 授权必须在打开文件流之前完成；解析器只接收受限大小的本地资源，文本也有硬上限。
     */
    @Override
    public AiExternalFileContent resolveChatFileContent(String filePublicId, Long userId) {
        ChatFile chatFile = chatFileService.getAuthorizedFile(filePublicId, userId);
        long size = chatFile.getSizeBytes() == null ? -1L : chatFile.getSizeBytes();
        if (size < 0 || size > MAX_PARSE_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("文件过大，AI 文档解析最大支持 " + MAX_PARSE_FILE_SIZE_BYTES + " 字节");
        }

        Resource resource = chatFileService.loadFileContent(chatFile);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, chatFile.getOriginalFilename());
        if (chatFile.getContentType() != null && !chatFile.getContentType().isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, chatFile.getContentType());
        }

        BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACTED_TEXT_CHARS);
        boolean truncated = false;
        try (InputStream inputStream = resource.getInputStream()) {
            new AutoDetectParser().parse(inputStream, handler, metadata, new ParseContext());
        } catch (SAXException e) {
            // BodyContentHandler 在到达字符上限时会停止解析；已提取的内容仍可安全返回。
            if (handler.toString().isBlank()) {
                throw new IllegalArgumentException("无法解析该文件的文本内容", e);
            }
            truncated = true;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取或解析该文件", e);
        }

        String text = handler.toString().trim();
        if (text.length() >= MAX_EXTRACTED_TEXT_CHARS) {
            truncated = true;
        }
        String detectedContentType = metadata.get(Metadata.CONTENT_TYPE);
        return new AiExternalFileContent(
                chatFile.getPublicId(),
                chatFile.getOriginalFilename(),
                detectedContentType == null ? chatFile.getContentType() : detectedContentType,
                text,
                truncated
        );
    }

    @Override
    public void sendToUser(Long userId, String eventType, Map<String, Object> payload) {
        Notification<Map<String, Object>> notification = new Notification<>();
        notification.setType(eventType == null || eventType.isBlank() ? "AI_TASK_COMPLETED" : eventType);
        notification.setPayload(payload);
        sseEmitterService.sendToUser(userId, notification);
    }

    private List<AiExternalMessageItem> convert(List<MessageItemVO> messages, Long fallbackUserId) {
        return messages.stream().map(m -> {
            AiExternalMessageItem item = new AiExternalMessageItem();
            String sender = null;
            if (m.getSender() != null && m.getSender().getNickname() != null) {
                sender = m.getSender().getNickname();
            } else if (fallbackUserId != null) {
                sender = fallbackUserId.toString();
            }
            item.setSender(sender);
            item.setContent(m.getContent());
            item.setTimestamp(m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            return item;
        }).collect(Collectors.toList());
    }
}
