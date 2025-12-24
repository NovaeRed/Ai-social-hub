package cn.redture.chat.service;

import cn.redture.chat.pojo.dto.CreateMessageDTO;
import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.common.pojo.vo.CursorPageResult;

import java.util.List;

public interface MessageService {

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
}
