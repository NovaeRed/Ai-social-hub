package cn.redture.app.bridge;

import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.chat.service.MessageService;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.integration.ai.dto.AiExternalMessageItem;
import cn.redture.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 引擎调用的外部服务桥接实现，负责将 AI 引擎需要的数据请求转发到对应的服务，并将结果转换成 AI 引擎需要的格式。
 */
@Service
@RequiredArgsConstructor
public class AiExternalServiceBridge implements AiExternalService {

    private final UserService userService;
    private final MessageService messageService;

    @Override
    public boolean isAiAnalysisEnabled(Long userId) {
        return userService.isAiAnalysisEnabled(userId);
    }

    @Override
    public List<AiExternalMessageItem> getUserRecentMessages(Long userId, int limit) {
        List<MessageItemVO> messages = messageService.getUserRecentMessages(userId, limit);
        return convert(messages, userId);
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
