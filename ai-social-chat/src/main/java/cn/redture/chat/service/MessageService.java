package cn.redture.chat.service;

import cn.redture.chat.pojo.dto.CreateMessageDTO;
import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.common.pojo.vo.CursorPageResult;

import java.util.List;

public interface MessageService {

    /**
     * 清空当前用户的会话聊天记录可见性。
     */
    void clearConversationMessages(Long currentUserId, String conversationPublicId);

    /**
     * 获取指定会话的消息（按时间倒序，基于游标）。
     */
    CursorPageResult<MessageItemVO> listMessages(String conversationPublicId, Long beforeMessageId, int limit);

    /**
     * 在指定会话中发送一条消息，并触发实时事件推送。
     */
    MessageItemVO createMessage(String conversationPublicId, Long senderUserId, CreateMessageDTO dto);

    /**
     * 获取用户最近发送的消息（跨会话）
     *
     * @param userId 用户 ID
     * @param limit  条数限制
     * @return 消息列表
     */
    List<MessageItemVO> getUserRecentMessages(Long userId, int limit);

    /**
     * 获取会话的最近N条消息（用于AI智能回复的上下文）
     *
     * @param conversationId 会话ID
     * @param limit          条数限制
     * @return 消息列表（时间逆序）
     */
    List<MessageItemVO> getRecentContextMessages(Long conversationId, int limit);

    /**
     * 获取会话的最近N条消息（用于AI智能回复的上下文）- 按会话公开ID
     *
     * @param conversationPublicId 会话公开ID
     * @param limit                条数限制
     * @return 消息列表（时间逆序）
     */
    List<MessageItemVO> getRecentContextMessages(String conversationPublicId, int limit);

    /**
     * 按ID列表获取消息（用于Persona Analysis显式选择）
     *
     * @param messageIds 消息ID列表
     * @return 消息列表（时间正序）
     */
    List<MessageItemVO> getMessagesByIds(List<Long> messageIds);

    /**
     * 获取用户在指定会话中的消息样本（用于Persona Analysis）
     *
     * @param conversationPublicId 会话公开ID
     * @param userId               用户ID
     * @param limit                条数限制
     * @return 消息列表
     */
    List<MessageItemVO> getUserMessagesInConversation(String conversationPublicId, Long userId, int limit);

    /**
     * 获取用户的最近N条消息样本（跨会话，用于Persona Analysis）
     *
     * @param userId 用户ID
     * @param limit  条数限制
     * @return 消息列表
     */
    List<MessageItemVO> getUserRecentMessagesForAnalysis(Long userId, int limit);
}
