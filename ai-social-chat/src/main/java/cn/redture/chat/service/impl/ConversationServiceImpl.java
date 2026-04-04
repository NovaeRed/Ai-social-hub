package cn.redture.chat.service.impl;

import cn.redture.chat.mapper.ConversationMapper;
import cn.redture.chat.mapper.ConversationMemberMapper;
import cn.redture.chat.mapper.MessageMapper;
import cn.redture.chat.pojo.dto.ConversationTimelineDTO;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.entity.ConversationMember;
import cn.redture.chat.pojo.entity.Message;
import cn.redture.chat.pojo.enums.ConversationTypeEnum;
import cn.redture.chat.pojo.vo.ConversationSummaryVO;
import cn.redture.chat.service.ConversationService;
import cn.redture.chat.util.converter.ConversationConverter;
import cn.redture.chat.util.converter.MessageConverter;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.pojo.vo.CursorPageResult;
import cn.redture.common.util.IdUtil;
import cn.redture.identity.service.FriendshipService;
import cn.redture.identity.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ConversationMemberMapper conversationMemberMapper;

    @Resource
    private UserService userService;

    @Resource
    private FriendshipService friendshipService;

    @Override
    public CursorPageResult<ConversationSummaryVO> listConversations(Long currentUserId, Long cursor, int limit) {

        List<ConversationTimelineDTO> dtos = conversationMapper.selectTimelineConversations(currentUserId, cursor, limit + 1);

        boolean hasMore = dtos.size() > limit;
        List<ConversationTimelineDTO> page = hasMore ? dtos.subList(0, limit) : dtos;

        List<ConversationSummaryVO> items = page.stream().map(dto -> {
            ConversationSummaryVO vo = new ConversationSummaryVO();
            vo.setPublicId(dto.getPublicId());
            vo.setType(dto.getType() != null ? dto.getType() : "UNKNOWN");
            vo.setName(dto.getName());
            vo.setUnreadCount(dto.getUnreadCount() != null ? dto.getUnreadCount() : 0L);

            if (dto.getLatestMessagePublicId() != null) {
                ConversationSummaryVO.LatestMessageVO msgVo = new ConversationSummaryVO.LatestMessageVO();
                msgVo.setPublicId(dto.getLatestMessagePublicId());
                msgVo.setContent(dto.getLatestMessageContent());
                msgVo.setCreatedAt(dto.getLatestMessageCreatedAt());
                vo.setLatestMessage(msgVo);
            }
            return vo;
        }).toList();

        CursorPageResult<ConversationSummaryVO> result = new CursorPageResult<>();
        result.setItems(items);
        result.setHasMore(hasMore);
        if (hasMore) {
            ConversationTimelineDTO last = page.get(limit - 1);
            result.setNextCursor(last.getLatestMessageId() != null ? last.getLatestMessageId() : last.getId());
        }
        return result;
    }

    @Override
    public Conversation createOrGetPrivateConversation(Long currentUserId, String targetUserPublicId) {
        // TODO: 未来可在此处增加好友关系、黑名单等业务约束校验

        // 1.1 判断两人是否为好友关系
        if (friendshipService.listFriends(currentUserId)
                .stream()
                .noneMatch(friend -> friend.getPublicId().equals(targetUserPublicId))) {
            throw new InvalidInputException("只能与好友创建私聊会话");
        }

        // 1.2 根据 targetUserPublicId 查找目标用户 ID
        Long targetUserId = userService.resolveUserIdByPublicId(targetUserPublicId);

        // 2. 查询是否已存在 PRIVATE 会话（当前用户与目标用户二人会话）
        Conversation existing = conversationMapper.selectPrivateConversationBetween(currentUserId, targetUserId);
        if (existing != null) {
            return existing;
        }

        // 3. 如不存在，则创建新会话并建立成员关系
        Conversation conversation = new Conversation();
        conversation.setPublicId(IdUtil.nextId());
        conversation.setType(ConversationTypeEnum.PRIVATE);
        conversationMapper.insert(conversation);

        ConversationMember selfMember = new ConversationMember();
        selfMember.setConversationId(conversation.getId());
        selfMember.setUserId(currentUserId);
        conversationMemberMapper.insert(selfMember);

        ConversationMember targetMember = new ConversationMember();
        targetMember.setConversationId(conversation.getId());
        targetMember.setUserId(targetUserId);
        conversationMemberMapper.insert(targetMember);

        return conversation;
    }
}
