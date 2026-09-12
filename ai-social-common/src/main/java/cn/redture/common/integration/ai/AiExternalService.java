package cn.redture.common.integration.ai;

import cn.redture.common.integration.ai.dto.AiExternalMessageItem;
import cn.redture.common.integration.ai.dto.AiExternalFileContent;

import java.util.List;

/**
 * AI 引擎所需的外部数据服务接口（由应用聚合层实现）。
 */
public interface AiExternalService {

    boolean isAiAnalysisEnabled(Long userId);

    List<AiExternalMessageItem> getRecentContextMessages(String conversationPublicId, int limit);

    List<AiExternalMessageItem> getMessagesByIds(List<Long> messageIds);

    List<AiExternalMessageItem> getUserMessagesInConversation(String conversationPublicId, Long userId, int limit);

    List<AiExternalMessageItem> getUserRecentMessagesForAnalysis(Long userId, int limit);

    /**
     * 读取当前用户有权限访问的聊天附件，并返回已提取、限长的纯文本。
     */
    AiExternalFileContent resolveChatFileContent(String filePublicId, Long userId);
}
