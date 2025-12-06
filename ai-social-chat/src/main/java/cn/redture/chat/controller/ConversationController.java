package cn.redture.chat.controller;

import cn.redture.chat.pojo.dto.CreatePrivateConversationRequestDTO;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.vo.ConversationSummaryVO;
import cn.redture.chat.pojo.vo.CursorPageResult;
import cn.redture.chat.service.ConversationService;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    @Resource
    private ConversationService conversationService;

    @GetMapping
    public RestResult<CursorPageResult<ConversationSummaryVO>> listConversations(@RequestParam(value = "cursor", required = false) Long cursor,
                                                                                 @RequestParam(value = "limit", defaultValue = "200") int limit) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        CursorPageResult<ConversationSummaryVO> page = conversationService.listConversations(currentUserId, cursor, limit);
        return RestResult.success(page);
    }

    @PostMapping
    public RestResult<String> createOrGetPrivateConversation(@RequestBody CreatePrivateConversationRequestDTO request) {
        if (request == null || request.getTargetUserPublicId() == null || request.getTargetUserPublicId().isBlank()) {
            throw new InvalidInputException("target_user_public_id 不能为空");
        }
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        Conversation conversation = conversationService.createOrGetPrivateConversation(currentUserId, request.getTargetUserPublicId());
        if (conversation == null) {
            throw new ResourceNotFoundException("无法创建或获取会话");
        }
        return RestResult.success(conversation.getPublicId());
    }


}
