package cn.redture.chat.controller;

import cn.redture.chat.pojo.dto.CreateMessageDTO;
import cn.redture.chat.pojo.vo.CursorPageResult;
import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.chat.service.MessageService;
import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conversations/{conversation_public_id}/messages")
public class MessageController {

    @Resource
    private MessageService messageService;

    @GetMapping
    public RestResult<CursorPageResult<MessageItemVO>> listMessages(@PathVariable("conversation_public_id") String conversationPublicId,
                                                                    @RequestParam(value = "before_message_id", required = false) Long beforeMessageId,
                                                                    @RequestParam(value = "limit", defaultValue = "50") int limit) {
        CursorPageResult<MessageItemVO> page = messageService.listMessages(conversationPublicId, beforeMessageId, limit);
        return RestResult.success(page);
    }

    @PostMapping
    public RestResult<MessageItemVO> createMessage(@PathVariable("conversation_public_id") String conversationPublicId,
                                                   @RequestBody CreateMessageDTO dto) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        MessageItemVO vo = messageService.createMessage(conversationPublicId, currentUserId, dto);
        return RestResult.created(vo);
    }

}
