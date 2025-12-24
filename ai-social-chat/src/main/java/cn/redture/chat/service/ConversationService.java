package cn.redture.chat.service;

import cn.redture.chat.pojo.vo.ConversationSummaryVO;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.common.pojo.vo.CursorPageResult;

public interface ConversationService {

    /**
    * 游标分页获取当前用户的会话列表。
    */
    CursorPageResult<ConversationSummaryVO> listConversations(Long currentUserId, Long cursor, int limit);

    /**
     * 创建或获取一个私聊会话（type = PRIVATE）。
     */
    Conversation createOrGetPrivateConversation(Long currentUserId, String targetUserPublicId);
}
