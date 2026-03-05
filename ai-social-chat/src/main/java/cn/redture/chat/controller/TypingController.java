package cn.redture.chat.controller;

import cn.redture.chat.mapper.ConversationMapper;
import cn.redture.chat.mapper.ConversationMemberMapper;
import cn.redture.chat.pojo.dto.TypingRequestDTO;
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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/conversations/typing")
public class TypingController {

    @Resource
    private TypingService typingService;

    @PostMapping
    public RestResult<Void> reportTyping(@RequestBody TypingRequestDTO typingRequestDTO) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();

        typingService.reportTyping(currentUserId, typingRequestDTO);

        return RestResult.noContent();
    }

    @DeleteMapping
    public RestResult<Void> stopTyping(@RequestBody TypingRequestDTO typingRequestDTO) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        typingService.stopTyping(currentUserId, typingRequestDTO);
        return RestResult.noContent();
    }
}
