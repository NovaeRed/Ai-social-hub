package cn.redture.chat.service.impl;

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
import cn.redture.identity.service.UserService;
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
    @Resource
    private UserService userService;

    private static final String TYPING_VALUE_KEY = "typing:status";
    private static final int TYPING_EXPIRE_SECONDS = 30;

    /**
     * 开始打字
     */
    @Override
    public void reportTyping(Long userId, TypingRequestDTO typingRequestDTO) {
        String conversationPublicId = typingRequestDTO == null ? null : typingRequestDTO.getConversationPublicId();
        String targetUserPublicId = typingRequestDTO == null ? null : typingRequestDTO.getTargetUserPublicId();

        // TODO 将逻辑改成直接判断是否存在并返回 Boolean 类型，避免查询整个对象
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPublicId, conversationPublicId));

        if (conversation == null) {
            throw new ResourceNotFoundException("会话");
        }

        if (targetUserPublicId == null || targetUserPublicId.isEmpty()) {
            throw new ResourceNotFoundException("对方用户");
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
        String typingKey = TYPING_VALUE_KEY + typingField;
        boolean wasTyping = Boolean.TRUE.equals(stringRedisTemplate.hasKey(typingKey));

        // 每次上报都刷新过期时间，避免持续输入时状态提前失效。
        stringRedisTemplate.opsForValue().set(typingKey, "1", Duration.ofSeconds(TYPING_EXPIRE_SECONDS));

        // 仅从非打字状态切换为打字状态时向对端推送一次，后续上报只作为续期心跳。
        if (!wasTyping) {
            pushTypingEvent(conversation, userId, "TYPING", targetUserPublicId);
        }

        log.debug("用户 {} 在会话 {} 开始打字", userId, conversationPublicId);
    }

    /**
     * 停止打字
     */
    @Override
    public void stopTyping(Long userId, TypingRequestDTO typingRequestDTO) {
        String conversationPublicId = typingRequestDTO == null ? null : typingRequestDTO.getConversationPublicId();
        String targetUserPublicId = typingRequestDTO == null ? null : typingRequestDTO.getTargetUserPublicId();

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
            stopTypingInternal(conversation, userId, typingField, targetUserPublicId);
        }
    }

    /**
     * 内部停止打字方法
     */
    private void stopTypingInternal(Conversation conversation, Long userId, String typingField, String targetUserPublicId) {
        stringRedisTemplate.delete(TYPING_VALUE_KEY + typingField);

        // 推送停止打字事件给其他成员
        pushTypingEvent(conversation, userId, "STOP_TYPING", targetUserPublicId);

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
    private void pushTypingEvent(Conversation conversation, Long userId, String eventType, String targetUserPublicId) {
        if (targetUserPublicId == null || targetUserPublicId.isBlank()) {
            return;
        }

        List<Long> resolvedTargetUserIds = userService.getUserIdsByPublicIds(List.of(targetUserPublicId));
        if (resolvedTargetUserIds == null || resolvedTargetUserIds.isEmpty()) {
            return;
        }

        Long peerUserId = resolvedTargetUserIds.getFirst();
        if (peerUserId.equals(userId)) {
            return;
        }

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

    private String buildTypingField(Long conversationId, Long userId) {
        return conversationId + ":" + userId;
    }

    @Data
    public static class TypingPayload {
        private String conversationPublicId;
        private Long userId;
    }
}