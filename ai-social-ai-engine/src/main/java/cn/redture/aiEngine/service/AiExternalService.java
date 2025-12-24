package cn.redture.aiEngine.service;

import cn.redture.aiEngine.pojo.dto.MessageItem;
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
}
