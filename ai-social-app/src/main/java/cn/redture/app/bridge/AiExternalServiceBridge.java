package cn.redture.app.bridge;

import cn.redture.aiEngine.pojo.dto.MessageItem;
import cn.redture.aiEngine.service.AiExternalService;
import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.chat.service.MessageService;
import cn.redture.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 引擎外部数据服务的桥接实现
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
    public List<MessageItem> getUserRecentMessages(Long userId, int limit) {
        List<MessageItemVO> messages = messageService.getUserRecentMessages(userId, limit);
        return messages.stream().map(m -> {
            MessageItem item = new MessageItem();
            item.setSender(userId.toString());
            item.setContent(m.getContent());
            item.setTimestamp(m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            return item;
        }).collect(Collectors.toList());
    }
}
