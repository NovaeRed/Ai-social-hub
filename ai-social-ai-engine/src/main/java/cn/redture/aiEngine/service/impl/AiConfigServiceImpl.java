package cn.redture.aiEngine.service.impl;

import cn.redture.aiEngine.mapper.*;
import cn.redture.aiEngine.pojo.dto.AiConfigDTO;
import cn.redture.aiEngine.pojo.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.pojo.entity.*;
import cn.redture.aiEngine.pojo.enums.AiPersonaTaskType;
import cn.redture.aiEngine.pojo.enums.ProfileType;
import cn.redture.aiEngine.pojo.model.AiConfigParams;
import cn.redture.aiEngine.pojo.vo.*;
import cn.redture.aiEngine.service.AiConfigService;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.util.JsonUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static cn.redture.common.constants.RedisConstants.PERSONA_TASK_QUEUE_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private static final String PERSONA_TIMELINE_COUNTER_KEY_PREFIX = "ai:persona:timeline:pending:";
    private static final String PERSONA_TIMELINE_LAST_TRIGGER_KEY_PREFIX = "ai:persona:timeline:last-trigger:";

    private final AiUserConfigMapper aiUserConfigMapper;
    private final UserAiProfileMapper userAiProfileMapper;
    private final UserAiContextMapper userAiContextMapper;
    private final AiModelCapabilityMapper aiModelCapabilityMapper;
    private final AiUsageStatsMapper aiUsageStatsMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiExternalService aiExternalService;

    // 批量分析触发的消息数阈值
    @Value("${ai.persona.timeline.message-threshold:20}")
    private int timelineMessageThreshold;

    // 批量分析的时间冷却，单位秒，默认6小时（21600秒）
    @Value("${ai.persona.timeline.cooldown-seconds:21600}")
    private long timelineCooldownSeconds;

    /**
     * 获取当前启用的模型能力列表，并聚合为前端可消费的模型选项。
     *
     * @return 模型选项列表
     */
    @Override
    public List<AiModelVO> getAvailableModels() {
        List<AiModelCapability> capabilities = aiModelCapabilityMapper.selectList(
                new LambdaQueryWrapper<AiModelCapability>().eq(AiModelCapability::getIsEnabled, true)
        );

        return capabilities.stream()
                .collect(Collectors.groupingBy(c -> c.getModelName() + ":" + c.getProvider()))
                .values().stream()
                .map(list -> {
                    AiModelCapability first = list.getFirst();
                    String optionCode = buildOptionCode(first.getProvider(), first.getModelName());
                    List<String> capabilitiesByModel = list.stream().map(c -> c.getCapabilityType().name()).collect(Collectors.toList());

                    ModelPricingVO pricing = new ModelPricingVO();
                    pricing.setInputPricePerMillion(first.getInputPricePerMillion());
                    pricing.setOutputPricePerMillion(first.getOutputPricePerMillion());

                    AiModelVO model = new AiModelVO();
                    model.setOptionCode(optionCode);
                    model.setDisplayName(first.getModelName());
                    model.setName(first.getModelName());
                    model.setProvider(first.getProvider());
                    model.setCapabilities(capabilitiesByModel);
                    model.setPricing(pricing);
                    model.setMaxTokens(first.getMaxTokens());
                    return model;
                })
                .collect(Collectors.toList());
    }

    /**
     * 保存用户托管 AI 配置（模型选项 + 开关项）。
     *
     * @param userId 用户 ID
     * @param config 用户提交的配置请求
     * @return 配置记录 ID
     */
    @Override
    public String setUserConfig(Long userId, AiConfigDTO config) {
        AiUserConfig userConfig = aiUserConfigMapper.selectOne(new LambdaQueryWrapper<AiUserConfig>()
                .eq(AiUserConfig::getUserId, userId));

        String selectedOptionCode = resolveSelectedOptionCode(config);
        AiConfigParams managedSwitches = sanitizeSwitches(config == null ? null : config.getPreferences());

        if (userConfig == null) {
            userConfig = new AiUserConfig();
            userConfig.setUserId(userId);
            userConfig.setDefaultModel(selectedOptionCode);
            userConfig.setConfigParams(managedSwitches);
            aiUserConfigMapper.insert(userConfig);
        } else {
            userConfig.setDefaultModel(selectedOptionCode);
            userConfig.setConfigParams(managedSwitches);
            aiUserConfigMapper.updateById(userConfig);
        }
        return userConfig.getId().toString();
    }

    /**
     * 查询用户 AI 配置，同时附带可选模型列表与当月用量摘要。
     *
     * @param userId 用户 ID
     * @return 用户 AI 配置视图
     */
    @Override
    public AiConfigVO getUserConfig(Long userId) {
        AiUserConfig userConfig = aiUserConfigMapper.selectOne(new LambdaQueryWrapper<AiUserConfig>()
                .eq(AiUserConfig::getUserId, userId));

        List<AiModelVO> modelOptions = getAvailableModels();
        String selectedModelOptionCode = userConfig != null ? userConfig.getDefaultModel() : null;
        if (selectedModelOptionCode == null || selectedModelOptionCode.isBlank()) {
            selectedModelOptionCode = modelOptions.isEmpty() ? "managed-default" : modelOptions.getFirst().getOptionCode();
        }
        AiConfigParams switches = sanitizeSwitches(userConfig == null ? null : userConfig.getConfigParams());

        // 获取本月用量统计
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        UsageSummaryVO usageSummary = aiUsageStatsMapper.getUsageSummary(userId, startOfMonth);

        UserUsageSummaryVO usage = new UserUsageSummaryVO();
        usage.setMonthlyTokens(usageSummary != null ? usageSummary.getTotalTokens() : 0L);
        usage.setMonthlyCost(usageSummary != null ? usageSummary.getTotalCost() : BigDecimal.ZERO);

        AiConfigVO response = new AiConfigVO();
        response.setSelectedModelOptionCode(selectedModelOptionCode);
        response.setModelOptions(modelOptions);
        response.setSwitches(switches);
        response.setUsage(usage);
        return response;
    }

    /**
     * 查询用户画像信息，可按画像类型过滤。
     *
     * @param userId 用户 ID
     * @param profileType 画像类型过滤条件
     * @return 用户画像列表
     */
    @Override
    public List<AiProfileVO> getUserProfiles(Long userId, String profileType) {
        LambdaQueryWrapper<UserAiProfile> queryWrapper = new LambdaQueryWrapper<UserAiProfile>()
                .eq(UserAiProfile::getUserId, userId);

        if (profileType != null && !profileType.trim().isEmpty()) {
            queryWrapper.eq(UserAiProfile::getProfileType, profileType.trim().toUpperCase());
        }

        List<UserAiProfile> profiles = userAiProfileMapper.selectList(queryWrapper);

        return profiles.stream().map(profile -> {
            AiProfileVO item = new AiProfileVO();
            item.setProfileType(profile.getProfileType());
            item.setContent(profile.getContent());
            item.setModelName(profile.getModelName());
            item.setProvider(profile.getProvider());
            item.setCreatedAt(profile.getCreatedAt());
            item.setUpdatedAt(profile.getUpdatedAt());
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 查询用户 AI 使用统计。
     *
     * @param userId 用户 ID
     * @param dateFrom 起始日期
     * @param dateTo 结束日期
     * @param provider 服务商过滤条件
     * @return 使用统计视图
     */
    @Override
    public AiUsageVO getUsageStats(Long userId, String dateFrom, String dateTo, String provider) {
        LocalDate from = dateFrom != null ? LocalDate.parse(dateFrom) : LocalDate.now().minusDays(30);
        LocalDate to = dateTo != null ? LocalDate.parse(dateTo) : LocalDate.now();

        UsageSummaryVO summary = aiUsageStatsMapper.getUsageSummary(userId, from);
        if (summary != null) {
            summary.setDateFrom(from.toString());
            summary.setDateTo(to.toString());
        }

        List<DailyUsageVO> daily = aiUsageStatsMapper.getDailyBreakdown(userId, from, to);
        List<ProviderUsageVO> byProvider = aiUsageStatsMapper.getUsageByProvider(userId, from, to);

        AiUsageVO usage = new AiUsageVO();
        usage.setSummary(summary);
        usage.setDailyBreakdown(daily);
        usage.setByProvider(byProvider);
        return usage;
    }

    /**
     * 处理用户 AI 分析授权变更事件。
     *
     * @param userId 用户 ID
     * @param enabled 是否启用
     */
    @Override
    public void onAiAnalysisToggled(Long userId, boolean enabled) {
        if (!enabled) {
            clearTimelineProgress(userId);
            enqueuePersonaTask(userId, AiPersonaTaskType.AI_PERSONA_AUTH_DISABLED);
            log.info("用户 {} 关闭 AI 画像授权，已投递异步清理任务", userId);
            return;
        }

        resetTimelineProgress(userId);
        log.info("用户 {} 开启 AI 画像授权，等待后续时间线触发分析", userId);
    }

    /**
     * 同步清理用户画像相关存储数据。
     *
     * @param userId 用户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearPersonaByUserId(Long userId) {
        int profiles = userAiContextMapper.deleteProfilesByUserId(userId);

        String personaCacheKey = "ai:persona:" + userId;
        stringRedisTemplate.delete(personaCacheKey);

        log.debug("清除用户 {} 的 AI 画像数据完成，profiles={}", userId, profiles);
    }

    /**
     * 异步投递用户画像清理任务。
     *
     * @param userId 用户 ID
     */
    @Override
    public void clearPersonaByUserIdAsync(Long userId) {
        enqueuePersonaTask(userId, AiPersonaTaskType.AI_PERSONA_CLEAR);
        log.info("用户 {} 发起手动清除画像，已投递异步任务", userId);
    }

    /**
     * 处理用户新消息事件，并按阈值与冷却策略触发画像分析任务。
     *
     * @param userId 用户 ID
     * @param messageTime 消息时间
     */
    @Override
    public void onUserMessageCreated(Long userId, OffsetDateTime messageTime) {
        if (userId == null) {
            return;
        }

        if (!aiExternalService.isAiAnalysisEnabled(userId)) {
            return;
        }

        String counterKey = PERSONA_TIMELINE_COUNTER_KEY_PREFIX + userId;
        Long pending = stringRedisTemplate.opsForValue().increment(counterKey);
        if (pending != null && pending == 1L) {
            stringRedisTemplate.expire(counterKey, 30, TimeUnit.DAYS);
        }

        int threshold = Math.max(timelineMessageThreshold, 1);
        if (pending == null || pending < threshold) {
            return;
        }

        if (!allowTriggerByCooldown(userId, messageTime)) {
            return;
        }

        enqueuePersonaTask(userId, AiPersonaTaskType.AI_PERSONA_ANALYSIS);
        stringRedisTemplate.opsForValue().set(counterKey, "0", 30, TimeUnit.DAYS);
        log.info("用户 {} 达到时间线画像阈值，已投递增量分析任务，pending={}", userId, pending);
    }

    /**
     * 校验是否满足冷却时间，满足时刷新最近触发时间戳。
     *
     * @param userId 用户 ID
     * @param messageTime 当前消息时间
     * @return true 表示允许触发，false 表示仍在冷却窗口
     */
    private boolean allowTriggerByCooldown(Long userId, OffsetDateTime messageTime) {
        String key = PERSONA_TIMELINE_LAST_TRIGGER_KEY_PREFIX + userId;
        String lastValue = stringRedisTemplate.opsForValue().get(key);
        long nowEpoch = (messageTime != null ? messageTime : OffsetDateTime.now()).toEpochSecond();

        if (lastValue != null) {
            try {
                long lastEpoch = Long.parseLong(lastValue);
                if (nowEpoch - lastEpoch < Math.max(timelineCooldownSeconds, 0)) {
                    return false;
                }
            } catch (NumberFormatException ignore) {
            }
        }

        long ttl = Math.max(timelineCooldownSeconds * 2, 60L);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(nowEpoch), ttl, TimeUnit.SECONDS);
        return true;
    }

    /**
     * 重置用户时间线分析进度计数与触发标记。
     *
     * @param userId 用户 ID
     */
    private void resetTimelineProgress(Long userId) {
        String counterKey = PERSONA_TIMELINE_COUNTER_KEY_PREFIX + userId;
        String triggerKey = PERSONA_TIMELINE_LAST_TRIGGER_KEY_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(counterKey, "0", 30, TimeUnit.DAYS);
        stringRedisTemplate.delete(triggerKey);
    }

    /**
     * 清空用户时间线分析进度计数与触发标记。
     *
     * @param userId 用户 ID
     */
    private void clearTimelineProgress(Long userId) {
        String counterKey = PERSONA_TIMELINE_COUNTER_KEY_PREFIX + userId;
        String triggerKey = PERSONA_TIMELINE_LAST_TRIGGER_KEY_PREFIX + userId;
        stringRedisTemplate.delete(counterKey);
        stringRedisTemplate.delete(triggerKey);
    }

    /**
     * 向画像任务队列投递任务。
     *
     * @param userId 用户 ID
     * @param taskType 任务类型
     */
    private void enqueuePersonaTask(Long userId, AiPersonaTaskType taskType) {
        AiPersonaTaskDTO task = new AiPersonaTaskDTO();
        task.setUserId(userId);
        task.setType(taskType);
        String taskJson = JsonUtil.toJson(task);
        stringRedisTemplate.opsForList().leftPush(PERSONA_TASK_QUEUE_KEY, taskJson);
    }

    /**
     * 解析用户选择的模型选项编码；若未提供则回退到可用模型中的第一个。
     *
     * @param config 配置请求
     * @return 模型选项编码
     */
    private String resolveSelectedOptionCode(AiConfigDTO config) {
        if (config != null && config.getModelOptionCode() != null && !config.getModelOptionCode().isBlank()) {
            return config.getModelOptionCode().trim();
        }

        List<AiModelVO> options = getAvailableModels();
        if (!options.isEmpty()) {
            return options.getFirst().getOptionCode();
        }

        return "managed-default";
    }

    /**
     * 规范化开关类配置，保证关键开关具备默认值。
     *
     * @param raw 原始开关配置
     * @return 规范化后的开关配置
     */
    private AiConfigParams sanitizeSwitches(AiConfigParams raw) {
        AiConfigParams params = new AiConfigParams();
        if (raw == null) {
            params.setAutoModeration(Boolean.TRUE);
            params.setSmartReplyEnabled(Boolean.TRUE);
            return params;
        }

        params.setAutoModeration(raw.getAutoModeration());
        params.setSmartReplyEnabled(raw.getSmartReplyEnabled());
        return params;
    }

    /**
     * 生成模型选项编码。
     *
     * @param provider 服务商标识
     * @param modelName 模型名称
     * @return 形如 provider:model 的模型选项编码
     */
    private String buildOptionCode(String provider, String modelName) {
        String safeProvider = provider == null ? "unknown" : provider.trim().toLowerCase();
        String safeModel = modelName == null ? "default" : modelName.trim().toLowerCase();
        return safeProvider + ":" + safeModel;
    }
}
