package cn.redture.aiEngine.service;

import cn.redture.aiEngine.pojo.dto.MessageItem;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 引擎所需的外部数据服务接口（由用户模块和聊天模块实现）
 */
public interface AiExternalService {

    /**
     * 检查用户是否开启了 AI 画像分析
     * @param userId 用户 ID
     * @return 是否开启
     */
    boolean isAiAnalysisEnabled(Long userId);

    /**
     * 获取用户最近的聊天记录
     * @param userId 用户 ID
     * @param limit 条数限制
     * @return 消息列表
     */
    List<MessageItem> getUserRecentMessages(Long userId, int limit);

    /**
     * 获取会话的最近N条消息（用于AI智能回复的上下文）
     * @param conversationPublicId 会话公开ID
     * @param limit 条数限制
     * @return 消息列表（按时间逆序）
     */
    List<MessageItem> getRecentContextMessages(String conversationPublicId, int limit);

    /**
     * 按ID列表获取消息（用于Persona Analysis显式选择）
     * @param messageIds 消息ID列表
     * @return 消息列表（按时间正序）
     */
    List<MessageItem> getMessagesByIds(List<Long> messageIds);

    /**
     * 获取用户在指定会话中的消息样本（用于Persona Analysis）
     * @param conversationPublicId 会话公开ID
     * @param userId 用户ID
     * @param limit 条数限制
     * @return 消息列表
     */
    List<MessageItem> getUserMessagesInConversation(String conversationPublicId, Long userId, int limit);

    /**
     * 获取用户的最近N条消息样本（跨会话，用于Persona Analysis）
     * @param userId 用户ID
     * @param limit 条数限制
     * @return 消息列表
     */
    List<MessageItem> getUserRecentMessagesForAnalysis(Long userId, int limit);
}
