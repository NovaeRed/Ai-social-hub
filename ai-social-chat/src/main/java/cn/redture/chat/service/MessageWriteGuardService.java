package cn.redture.chat.service;

import cn.redture.chat.pojo.dto.CreateMessageDTO;
import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.common.resilience.ResilienceResourceNames;
import cn.redture.common.resilience.SentinelProtectionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 消息写入保护层：承接限流/熔断治理，避免控制器直接耦合 Sentinel 细节。
 */
@Service
public class MessageWriteGuardService {

    @Resource
    private MessageService messageService;

    @Resource
    private SentinelProtectionService sentinelProtectionService;

    public MessageItemVO createMessage(String conversationPublicId, Long currentUserId, CreateMessageDTO dto) {
        return sentinelProtectionService.call(
                ResilienceResourceNames.CHAT_CREATE_MESSAGE,
                () -> messageService.createMessage(conversationPublicId, currentUserId, dto)
        );
    }
}
