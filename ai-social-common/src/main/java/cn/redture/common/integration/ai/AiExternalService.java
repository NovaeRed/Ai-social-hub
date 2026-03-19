package cn.redture.common.integration.ai;

import cn.redture.common.integration.ai.dto.AiExternalMessageItem;

import java.util.List;

/**
 * AI 引擎所需的外部数据服务接口（由应用聚合层实现）。
 */
public interface AiExternalService {

    boolean isAiAnalysisEnabled(Long userId);

    List<AiExternalMessageItem> getUserRecentMessages(Long userId, int limit);

    List<AiExternalMessageItem> getRecentContextMessages(String conversationPublicId, int limit);

    List<AiExternalMessageItem> getMessagesByIds(List<Long> messageIds);

    List<AiExternalMessageItem> getUserMessagesInConversation(String conversationPublicId, Long userId, int limit);

    List<AiExternalMessageItem> getUserRecentMessagesForAnalysis(Long userId, int limit);
}
