package cn.redture.chat.service.impl;

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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class TypingServiceImpl implements TypingService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ConversationMapper conversationMapper;
    @Resource
    private ConversationMemberMapper conversationMemberMapper;
    @Resource
    private SseEmitterService sseEmitterService;

    private static final String TYPING_VALUE_KEY = "typing:status";
    private static final int TYPING_EXPIRE_SECONDS = 30;

    /**
     * 开始打字
     */
    @Override
    public void reportTyping(String conversationPublicId, Long userId) {
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPublicId, conversationPublicId));

        if (conversation == null) {
            throw new ResourceNotFoundException("会话");
        }

        if (conversation.getType() != ConversationTypeEnum.PRIVATE) {
            return;
        }

        ConversationMember member = conversationMemberMapper.selectByConversationIdAndUserId(
                conversation.getId(), userId);
        if (member == null) {
            throw new InvalidInputException("用户不是会话成员");
        }

        String typingField = buildTypingField(conversation.getId(), userId);

        if (isUserTyping(conversation.getId(), userId)) {
            // 如果用户已经在打字，先推送停止事件（避免状态不一致）
            stopTypingInternal(conversation, userId, typingField);
        }

        stringRedisTemplate.opsForValue().set(TYPING_VALUE_KEY + typingField, "1", Duration.ofSeconds(TYPING_EXPIRE_SECONDS));

        pushTypingEvent(conversation, userId, "TYPING");

        log.debug("用户 {} 在会话 {} 开始打字", userId, conversationPublicId);
    }

    /**
     * 停止打字
     */
    @Override
    public void stopTyping(String conversationPublicId, Long userId) {
        Conversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getPublicId, conversationPublicId)
        );

        if (conversation == null || conversation.getType() != ConversationTypeEnum.PRIVATE) {
            return;
        }

        String typingField = buildTypingField(conversation.getId(), userId);

        // 检查是否存在该打字状态
        if (isUserTyping(conversation.getId(), userId)) {
            stopTypingInternal(conversation, userId, typingField);
        }
    }

    /**
     * 内部停止打字方法
     */
    private void stopTypingInternal(Conversation conversation, Long userId, String typingField) {
        // 从 Redis Hash 中移除
        stringRedisTemplate.delete(TYPING_VALUE_KEY + typingField);

        // 推送停止打字事件给其他成员
        pushTypingEvent(conversation, userId, "STOP_TYPING");

        log.debug("用户 {} 在会话 {} 停止打字", userId, conversation.getPublicId());
    }

    /**
     * 检查指定用户是否在指定会话中打字
     */
    public boolean isUserTyping(Long conversationId, Long userId) {
        return stringRedisTemplate.hasKey(TYPING_VALUE_KEY + buildTypingField(conversationId, userId));
    }

    /**
     * 清理指定会话的所有打字状态
     */
    public void clearConversationTyping(Long conversationId, Long userId) {
        stringRedisTemplate.delete(TYPING_VALUE_KEY + buildTypingField(conversationId, userId));
    }

    /**
     * 推送打字事件给会话中的对方用户
     */
    private void pushTypingEvent(Conversation conversation, Long userId, String eventType) {
        List<ConversationMember> members = conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversation.getId()));

        // 找到对方用户ID
        Long peerUserId = members.stream()
                .map(ConversationMember::getUserId)
                .filter(peerId -> !peerId.equals(userId))
                .findFirst()
                .orElse(null);

        if (peerUserId != null) {
            // 构建通知载荷
            TypingPayload payload = new TypingPayload();
            payload.setConversationPublicId(conversation.getPublicId());
            payload.setUserId(userId);

            Notification<TypingPayload> notification = Notification.<TypingPayload>builder()
                    .type(eventType)
                    .payload(payload)
                    .build();

            try {
                sseEmitterService.sendToUser(peerUserId, notification);
            } catch (Exception e) {
                log.warn("向用户 {} 推送打字事件失败: {}", peerUserId, e.getMessage());
            }
        }
    }

    private String buildTypingField(Long conversationId, Long userId) {
        return conversationId + ":" + userId;
    }

    @Data
    public static class TypingPayload {
        private String conversationPublicId;
        private Long userId;
    }
}