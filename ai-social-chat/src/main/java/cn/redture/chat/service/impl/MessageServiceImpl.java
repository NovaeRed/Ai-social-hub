package cn.redture.chat.service.impl;

import cn.redture.chat.mapper.ConversationMapper;
import cn.redture.chat.mapper.ConversationMemberMapper;
import cn.redture.chat.mapper.MessageMapper;
import cn.redture.chat.pojo.dto.CreateMessageDTO;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.entity.ConversationMember;
import cn.redture.chat.pojo.entity.Message;
import cn.redture.chat.pojo.enums.MediaTypeEnum;
import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.chat.service.MessageService;
import cn.redture.chat.sse.Notification;
import cn.redture.chat.sse.SseEmitterService;
import cn.redture.chat.util.converter.MessageConverter;
import cn.redture.common.event.ai.UserMessageCreatedEvent;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.pojo.vo.CursorPageResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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

    @Resource
    private ApplicationEventPublisher eventPublisher;

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

        // sender 信息后续可通过联表/批量查询用户表填充
        List<MessageItemVO> items = page.stream().map(MessageConverter.INSTANCE::toMessageItemVO).toList();

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

        // 非侵入式时间线触发：仅记录用户新增消息事件，真实分析由异步任务消费完成。
        try {
            eventPublisher.publishEvent(new UserMessageCreatedEvent(senderUserId, message.getCreatedAt()));
        } catch (Exception e) {
            log.warn("记录用户 {} 画像时间线消息失败，不影响消息主流程", senderUserId, e);
        }

        return vo;
    }

    @Override
    public List<MessageItemVO> getUserRecentMessages(Long userId, int limit) {
        List<Message> messages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, userId)
                .orderByDesc(Message::getCreatedAt)
                .last("LIMIT " + limit));

        return messages.stream()
                .map(MessageConverter.INSTANCE::toMessageItemVO)
                .toList();
    }

    /**
     * 获取会话最近的上文消息 (用于 Smart Reply)
     * 用于生成 AI 响应建议时的上下文
     *
     * @param conversationId 会话ID
     * @param limit          消息数量
     * @return 消息VO列表（按时间逆序）
     */
    @Override
    public List<MessageItemVO> getRecentContextMessages(Long conversationId, int limit) {
        try {
            // 查询最近的消息（按时间倒序）
            List<Message> messages = messageMapper.selectRecentMessages(conversationId, limit);
            
            log.info("获取会话 {} 的最近 {} 条上下文消息，实际获取 {} 条", conversationId, limit, messages.size());
            
            if (messages.isEmpty()) {
                log.warn("会话 {} 没有消息上下文", conversationId);
                return List.of();
            }
            
            return messages.stream()
                    .map(MessageConverter.INSTANCE::toMessageItemVO)
                    .toList();
        } catch (Exception e) {
            log.error("获取上下文消息时出错，会话ID: {}", conversationId, e);
            return List.of();
        }
    }

    /**
     * 获取会话最近的上文消息 (用于 Smart Reply) - 按公开ID
     * 用于生成 AI 响应建议时的上下文
     *
     * @param conversationPublicId 会话公开ID
     * @param limit                消息数量
     * @return 消息VO列表（按时间逆序）
     */
    @Override
    public List<MessageItemVO> getRecentContextMessages(String conversationPublicId, int limit) {
        try {
            // 1. 按 publicId 查询会话
            Conversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getPublicId, conversationPublicId));
            
            if (conv == null) {
                log.warn("会话不存在：{}", conversationPublicId);
                return List.of();
            }
            
            // 2. 按会话ID查询消息
            return getRecentContextMessages(conv.getId(), limit);
        } catch (Exception e) {
            log.error("获取会话 {} 的上下文消息时出错", conversationPublicId, e);
            return List.of();
        }
    }

    /**
     * 按消息ID列表查询消息 (用于 Persona Analysis)
     * 获取用户明确选择的消息
     *
     * @param messageIds 消息ID列表
     * @return 消息VO列表
     */
    @Override
    public List<MessageItemVO> getMessagesByIds(List<Long> messageIds) {
        try {
            if (messageIds == null || messageIds.isEmpty()) {
                log.warn("消息ID列表为空");
                return List.of();
            }
            
            // 将 List<Long> 转换为逗号分隔的字符串用于 SQL IN 子句
            String ids = messageIds.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            
            List<Message> messages = messageMapper.selectByIds(ids);
            log.info("查询 {} 条指定消息，实际获取 {} 条", messageIds.size(), messages.size());
            
            return messages.stream()
                    .map(MessageConverter.INSTANCE::toMessageItemVO)
                    .toList();
        } catch (Exception e) {
            log.error("查询指定消息时出错，消息IDs: {}", messageIds, e);
            return List.of();
        }
    }

    /**
     * 获取用户在特定会话中的消息 (用于 Persona Analysis 展示列表)
     * 用于让用户选择要分析的消息
     *
     * @param conversationPublicId 会话公开ID
     * @param userId               用户ID
     * @param limit                消息数量
     * @return 消息VO列表（按时间倒序，最新的在前）
     */
    @Override
    public List<MessageItemVO> getUserMessagesInConversation(String conversationPublicId, Long userId, int limit) {
        try {
            Conversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getPublicId, conversationPublicId));

            if (conv == null) {
                log.error("会话不存在：{}", conversationPublicId);
                return List.of();
            }

            List<Message> messages = messageMapper.selectByConversationAndUser(conv.getId(), userId, limit);
            log.info("获取用户 {} 在会话 {} 中的 {} 条消息，实际获取 {} 条", userId, conv.getId(), limit, messages.size());

            return messages.stream()
                    .map(MessageConverter.INSTANCE::toMessageItemVO)
                    .toList();
        } catch (Exception e) {
            log.error("获取用户消息时出错，用户ID: {}，会话公开ID: {}", userId, conversationPublicId, e);
            return List.of();
        }
    }

    /**
     * 获取用户最近的消息（跨会话）(用于 Persona Analysis 隐私模式)
     * 从用户的最近消息中进行采样分析
     *
     * @param userId 用户ID
     * @param limit  消息数量
     * @return 消息VO列表
     */
    @Override
    public List<MessageItemVO> getUserRecentMessagesForAnalysis(Long userId, int limit) {
        try {
            List<Message> messages = messageMapper.selectByUserId(userId, limit);
            log.info("获取用户 {} 的最近 {} 条消息用于分析，实际获取 {} 条", userId, limit, messages.size());
            
            return messages.stream()
                    .map(MessageConverter.INSTANCE::toMessageItemVO)
                    .toList();
        } catch (Exception e) {
            log.error("获取用户最近消息时出错，用户ID: {}", userId, e);
            return List.of();
        }
    }
}
