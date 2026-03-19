package cn.redture.aiEngine.service;

import cn.redture.aiEngine.pojo.dto.AiConfigDTO;
import cn.redture.aiEngine.pojo.vo.AiConfigVO;
import cn.redture.aiEngine.pojo.vo.AiModelVO;
import cn.redture.aiEngine.pojo.vo.AiProfileVO;
import cn.redture.aiEngine.pojo.vo.AiUsageVO;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI配置与信息服务接口
 */
public interface AiConfigService {

    /**
     * 获取可用AI模型列表
     *
     * @return 模型列表
     */
    List<AiModelVO> getAvailableModels();

    /**
     * 设置用户AI配置
     *
     * @param userId 用户ID
     * @param config 配置DTO
     * @return 配置ID
     */
    String setUserConfig(Long userId, AiConfigDTO config);

    /**
     * 获取用户AI配置
     *
     * @param userId 用户ID
     * @return 配置VO
     */
    AiConfigVO getUserConfig(Long userId);

    /**
     * 获取用户AI画像
     *
     * @param userId      用户ID
     * @param profileType 画像类型过滤
     * @return 画像列表
     */
    List<AiProfileVO> getUserProfiles(Long userId, String profileType);

    /**
     * 获取AI使用统计
     *
     * @param userId   用户ID
     * @param dateFrom 开始日期
     * @param dateTo   结束日期
     * @param provider 提供商过滤
     * @return 使用统计VO
     */
    AiUsageVO getUsageStats(Long userId, String dateFrom, String dateTo, String provider);

    /**
     * 当用户的 AI 分析授权开关发生变化时触发。
     *
     * @param userId          内部用户ID
     * @param enabled         新的授权状态
     */
    void onAiAnalysisToggled(Long userId, boolean enabled);

    /**
     * 清除指定用户的 AI 画像（user_ai_profiles）及相关缓存。
     *
     * @param userId 内部用户ID
     */
    void clearPersonaByUserId(Long userId);

    /**
     * 异步清除指定用户的 AI 画像，调用方只负责投递任务。
     *
     * @param userId 内部用户ID
     */
    void clearPersonaByUserIdAsync(Long userId);

    /**
     * 记录用户新增消息事件，用于时间线增量触发画像分析。
     *
     * @param userId 用户ID
     * @param messageTime 消息时间
     */
    void onUserMessageCreated(Long userId, OffsetDateTime messageTime);
}
