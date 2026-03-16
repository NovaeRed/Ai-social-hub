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
import cn.redture.common.util.JsonUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static cn.redture.common.constants.RedisConstants.PERSONA_TASK_QUEUE_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private final AiUserConfigMapper aiUserConfigMapper;
    private final UserAiProfileMapper userAiProfileMapper;
    private final UserAiContextMapper userAiContextMapper;
    private final AiProviderConfigMapper aiProviderConfigMapper;
    private final AiModelCapabilityMapper aiModelCapabilityMapper;
    private final AiUsageStatsMapper aiUsageStatsMapper;
    private final StringRedisTemplate stringRedisTemplate;

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
                    return AiModelVO.builder()
                            .name(first.getModelName())
                            .provider(first.getProvider())
                            .capabilities(list.stream().map(c -> c.getCapabilityType().name()).collect(Collectors.toList()))
                            .pricing(ModelPricingVO.builder()
                                    .inputPricePerMillion(first.getInputPricePerMillion())
                                    .outputPricePerMillion(first.getOutputPricePerMillion())
                                    .build())
                            .maxTokens(first.getMaxTokens())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public String setUserConfig(Long userId, AiConfigDTO config) {
        AiUserConfig userConfig = aiUserConfigMapper.selectOne(new LambdaQueryWrapper<AiUserConfig>()
                .eq(AiUserConfig::getUserId, userId));

        if (userConfig == null) {
            userConfig = new AiUserConfig();
            userConfig.setUserId(userId);
            userConfig.setDefaultModel(config.getDefaultModel());
            userConfig.setDefaultProvider(config.getDefaultProvider());
            userConfig.setConfigParams(config.getPreferences());
            aiUserConfigMapper.insert(userConfig);
        } else {
            userConfig.setDefaultModel(config.getDefaultModel());
            userConfig.setDefaultProvider(config.getDefaultProvider());
            userConfig.setConfigParams(config.getPreferences());
            aiUserConfigMapper.updateById(userConfig);
        }
        return userConfig.getId().toString();
    }

    @Override
    public AiConfigVO getUserConfig(Long userId) {
        AiUserConfig userConfig = aiUserConfigMapper.selectOne(new LambdaQueryWrapper<AiUserConfig>()
                .eq(AiUserConfig::getUserId, userId));

        List<String> providers = aiProviderConfigMapper.selectList(
                new LambdaQueryWrapper<AiProviderConfig>().eq(AiProviderConfig::getIsEnabled, true)
        ).stream().map(AiProviderConfig::getProviderName).collect(Collectors.toList());

        String defaultModel = userConfig != null ? userConfig.getDefaultModel() : "gpt-4o";
        AiConfigParams preferences = userConfig != null ? userConfig.getConfigParams() : null;

        // 获取本月用量统计
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        UsageSummaryVO usageSummary = aiUsageStatsMapper.getUsageSummary(userId, startOfMonth);

        return AiConfigVO.builder()
                .defaultModel(defaultModel)
                .providers(providers)
                .preferences(preferences)
                .usage(UserUsageSummaryVO.builder()
                        .monthlyTokens(usageSummary != null ? usageSummary.getTotalTokens() : 0L)
                        .monthlyCost(usageSummary != null ? usageSummary.getTotalCost() : BigDecimal.ZERO)
                        .build())
                .build();
    }

    @Override
    public List<AiProfileVO> getUserProfiles(Long userId, String profileType) {
        LambdaQueryWrapper<UserAiProfile> queryWrapper = new LambdaQueryWrapper<UserAiProfile>()
                .eq(UserAiProfile::getUserId, userId);

        // 安全过滤 profileType（支持字符串匹配枚举）
        if (profileType != null && !profileType.trim().isEmpty()) {
            queryWrapper.eq(UserAiProfile::getProfileType, profileType.trim().toUpperCase());
        }

        List<UserAiProfile> profiles = userAiProfileMapper.selectList(queryWrapper);

        return profiles.stream().map(profile -> AiProfileVO.builder()
                .profileType(profile.getProfileType())
                .content(profile.getContent())
                .provider(profile.getProvider())
                .modelName(profile.getModelName())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build()
        ).collect(Collectors.toList());
    }

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

        return AiUsageVO.builder()
                .summary(summary)
                .dailyBreakdown(daily)
                .byProvider(byProvider)
                .build();
    }

    @Override
    public void onAiAnalysisToggled(Long userId, boolean enabled) {
        if (!enabled) {
            enqueuePersonaTask(userId, AiPersonaTaskType.AI_PERSONA_AUTH_DISABLED);
            log.info("用户 {} 关闭 AI 画像授权，已投递异步清理任务", userId);
            return;
        }
        log.info("用户 {} 开启 AI 画像授权，等待后续时间线触发分析", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearPersonaByUserId(Long userId) {
        int embeddings = userAiContextMapper.clearEmbeddingByUserId(userId);
        int profiles = userAiContextMapper.deleteProfilesByUserId(userId);

        String personaCacheKey = "ai:persona:" + userId;
        stringRedisTemplate.delete(personaCacheKey);

        log.debug("清除用户 {} 的 AI 画像数据完成，profiles={}, embeddings={}", userId, profiles, embeddings);
    }

    @Override
    public void clearPersonaByUserIdAsync(Long userId) {
        enqueuePersonaTask(userId, AiPersonaTaskType.AI_PERSONA_CLEAR);
        log.info("用户 {} 发起手动清除画像，已投递异步任务", userId);
    }

    private void enqueuePersonaTask(Long userId, AiPersonaTaskType taskType) {
        AiPersonaTaskDTO task = new AiPersonaTaskDTO();
        task.setUserId(userId);
        task.setType(taskType);
        String taskJson = JsonUtil.toJson(task);
        stringRedisTemplate.opsForList().leftPush(PERSONA_TASK_QUEUE_KEY, taskJson);
    }
}
