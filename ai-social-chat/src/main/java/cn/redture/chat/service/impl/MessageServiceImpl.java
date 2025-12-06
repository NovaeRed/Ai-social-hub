package cn.redture.chat.service.impl;

import cn.redture.chat.mapper.ConversationMapper;
import cn.redture.chat.mapper.ConversationMemberMapper;
import cn.redture.chat.mapper.MessageMapper;
import cn.redture.chat.pojo.dto.CreateMessageDTO;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.entity.ConversationMember;
import cn.redture.chat.pojo.entity.Message;
import cn.redture.chat.pojo.enums.MediaTypeEnum;
import cn.redture.chat.pojo.vo.CursorPageResult;
import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.chat.service.MessageService;
import cn.redture.chat.sse.Notification;
import cn.redture.chat.sse.SseEmitterService;
import cn.redture.chat.util.converter.MessageConverter;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.util.SecurityContextHolderUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private ConversationMemberMapper conversationMemberMapper;

    @Resource
    private SseEmitterService sseEmitterService;

    @Override
    public CursorPageResult<MessageItemVO> listMessages(String conversationPublicId, Long beforeMessageId, int limit) {
        Long userId = SecurityContextHolderUtil.getUserId();

        Conversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPublicId, conversationPublicId));
        if (conv == null) {
            return new CursorPageResult<>();
        }
        Long conversationId = conv.getId();

        // 1. 验证用户是否为会话成员
        ConversationMember member = conversationMemberMapper.selectByConversationIdAndUserId(conversationId, userId);
        if (member == null) {
            throw new ResourceNotFoundException("会话不存在或无权限访问");
        }

        // 2. 查询消息
        List<Message> messages = messageMapper.selectByCursor(conversationId, beforeMessageId, limit + 1);

        boolean hasMore = messages.size() > limit;
        List<Message> page = hasMore ? messages.subList(0, limit) : messages;

        List<MessageItemVO> items = page.stream().map(msg -> {
            MessageItemVO vo = MessageConverter.INSTANCE.toMessageItemVO(msg);
            if (msg.getParentMessageId() != null) {
                // 这里只能先填 null，占位：需要根据 parentMessageId 再查一遍获取其 public_id
                vo.setParentMessagePublicId(null);
            }
            // sender 信息后续可通过联表/批量查询用户表填充
            return vo;
        }).toList();

        CursorPageResult<MessageItemVO> result = new CursorPageResult<>();
        result.setItems(items);
        result.setHasMore(hasMore);

        if (!page.isEmpty()) {
            result.setNextCursor(page.getLast().getId());
        } else {
            result.setNextCursor(null);
        }

        // 3. 更新已读消息位置 - 使用最新消息的 ID（列表头部）
        if (!page.isEmpty()) {
            Long lastReadMessageId = member.getLastReadMessageId();
            Long latestMessageId = page.getFirst().getId();

            if (latestMessageId > lastReadMessageId) {
                conversationMemberMapper.updateLastReadMessageId(conversationId, userId, latestMessageId);
            }
        }

        return result;
    }

    @Override
    public MessageItemVO createMessage(String conversationPublicId, Long senderUserId, CreateMessageDTO dto) {
        Conversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPublicId, conversationPublicId));
        if (conv == null) {
            throw new ResourceNotFoundException("会话不存在");
        }

        // 1. 判断用户是否为会话成员
        ConversationMember memberCheck = conversationMemberMapper.selectByConversationIdAndUserId(conv.getId(), senderUserId);
        if (memberCheck == null) {
            throw new ResourceNotFoundException("会话不存在或无权限发送消息");
        }

        Message message = new Message();
        message.setConversationId(conv.getId());
        message.setSenderId(senderUserId);
        message.setContent(dto.getContent());
        message.setMediaType(MediaTypeEnum.TEXT);
        message.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(message);

        // 2. 更新会话的最新消息ID
        conv.setLatestMessageId(message.getId());
        conversationMapper.updateById(conv);

        MessageItemVO vo = MessageConverter.INSTANCE.toMessageItemVO(message);

        // 3. 推送 SSE 事件给会话中的其他成员
        List<ConversationMember> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conv.getId()));
        Notification<MessageItemVO> notification = Notification.<MessageItemVO>builder()
                .type("MESSAGE_CREATED")
                .payload(vo)
                .build();
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(senderUserId)) {
                sseEmitterService.sendToUser(member.getUserId(), notification);
            }
        }

        return vo;
    }
}
