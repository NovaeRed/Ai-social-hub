package cn.redture.chat.controller;

import cn.redture.chat.mapper.ConversationMapper;
import cn.redture.chat.mapper.ConversationMemberMapper;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.entity.ConversationMember;
import cn.redture.chat.pojo.enums.ConversationTypeEnum;
import cn.redture.chat.service.TypingService;
import cn.redture.chat.sse.Notification;
import cn.redture.chat.sse.SseEmitterService;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/conversations/{conversation_public_id}/typing")
public class TypingController {

    @Resource
    private TypingService typingService;

    @PostMapping
    public RestResult<Void> reportTyping(@PathVariable("conversation_public_id") String conversationPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();

        typingService.reportTyping(conversationPublicId, currentUserId);

        return RestResult.noContent();
    }

    @DeleteMapping
    public RestResult<Void> stopTyping(@PathVariable("conversation_public_id") String conversationPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        typingService.stopTyping(conversationPublicId, currentUserId);
        return RestResult.noContent();
    }
}
