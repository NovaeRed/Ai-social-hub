package cn.redture.aiEngine.service.impl;

import cn.redture.aiEngine.handler.AiFacadeHandler;
import cn.redture.aiEngine.mapper.UserAiProfileMapper;
import cn.redture.aiEngine.pojo.dto.*;
import cn.redture.aiEngine.pojo.entity.AiTask;
import cn.redture.aiEngine.pojo.entity.UserAiProfile;
import cn.redture.aiEngine.pojo.enums.AiProvider;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.vo.PersonaAnalysisResultVO;
import cn.redture.aiEngine.pojo.vo.ScheduleExtractionVO;
import cn.redture.aiEngine.pojo.vo.StreamOutputVO;
import cn.redture.aiEngine.service.AiInteractionService;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.exception.JsonException;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.integration.ai.dto.AiExternalMessageItem;
import cn.redture.common.util.JsonUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI交互服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInteractionServiceImpl implements AiInteractionService {

    private static final String PERSONA_TIMELINE_COUNTER_KEY_PREFIX = "ai:persona:timeline:pending:";
    private static final String PERSONA_TIMELINE_LAST_TRIGGER_KEY_PREFIX = "ai:persona:timeline:last-trigger:";
    private static final int PERSONA_TIMELINE_FETCH_MAX = 500;

    private final AiTaskService aiTaskService;
    private final AiFacadeHandler aiFacadeHandler;
    private final UserAiProfileMapper userAiProfileMapper;
    private final AiExternalService aiExternalService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 执行文本润色任务（流式返回）。
     *
     * @param userId  用户 ID
     * @param request 润色请求
     * @return 流式输出结果
     */
    @Override
    public Flux<StreamOutputVO> polishStream(Long userId, PolishRequest request) {
        log.info("Polish request from user: {}", userId);
        Map<String, Object> params = new HashMap<>();
        params.put("message", request.getMessage());
        if (request.getModelOptionCode() != null && !request.getModelOptionCode().isBlank()) {
            params.put("model_option_code", request.getModelOptionCode().trim());
        }
        return executeStreamTask(userId, AiTaskType.POLISH, params, "polished_message");
    }

    /**
     * 执行翻译任务（流式返回）。
     *
     * @param userId  用户 ID
     * @param request 翻译请求
     * @return 流式输出结果
     */
    @Override
    public Flux<StreamOutputVO> translateStream(Long userId, TranslationRequest request) {
        log.info("Translation request from user: {}", userId);
        Map<String, Object> params = new HashMap<>();
        params.put("text", request.getText());
        params.put("targetLanguage", request.getTargetLanguage());
        if (request.getDomain() != null) {
            params.put("domain", request.getDomain());
        }
        if (request.getModelOptionCode() != null && !request.getModelOptionCode().isBlank()) {
            params.put("model_option_code", request.getModelOptionCode().trim());
        }
        return executeStreamTask(userId, AiTaskType.TRANSLATION, params, "translation");
    }

    /**
     * 执行智能回复建议任务（流式返回）。
     *
     * @param userId  用户 ID
     * @param request 智能回复请求
     * @return 流式输出结果
     */
    @Override
    public Flux<StreamOutputVO> smartReplyStream(Long userId, SmartReplyRequest request) {

        log.info("Smart reply request from user: {}, conversation: {}", userId, request.getConversationPublicId());

        Map<String, Object> params = new HashMap<>();
        params.put("message", request.getMessage());

        // 获取对话历史作为上下文
        // 优先使用请求中提供的历史（如果已经提供）
        // 否则后端会在AiFacadeHandler中从数据库自动选取
        if (request.getConversationHistory() != null && !request.getConversationHistory().isEmpty()) {
            params.put("conversation_history", request.getConversationHistory());
            log.debug("Using provided conversation history with {} messages", request.getConversationHistory().size());
        } else if (request.getConversationPublicId() != null) {
            // 标记需要从数据库自动选择上下文
            // Controller 或其他调用者应该在此之前已经设置了上下文
            log.debug("Conversation context will be handled by Controller auto-selection");
            params.put("conversation_public_id", request.getConversationPublicId());
        }

        if (request.getUserProfile() != null) {
            params.put("user_profile", request.getUserProfile());
        }
        if (request.getModelOptionCode() != null && !request.getModelOptionCode().isBlank()) {
            params.put("model_option_code", request.getModelOptionCode().trim());
        }

        return executeStreamTask(userId, AiTaskType.SMART_REPLY, params, "reply");
    }

    /**
     * 执行内容总结任务（流式返回）。
     *
     * @param userId  用户 ID
     * @param request 总结请求
     * @return 流式输出结果
     */
    @Override
    public Flux<StreamOutputVO> summarizeStream(Long userId, SummarizeRequest request) {
        log.info("Summarize request from user: {}", userId);
        Map<String, Object> params = new HashMap<>();
        params.put("content", request.getContent());
        params.put("summary_type", request.getSummaryType() != null ? request.getSummaryType() : "general");
        params.put("target_length", request.getTargetLength() != null ? request.getTargetLength() : "medium");

        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            params.put("keywords", request.getKeywords());
        }
        if (request.getModelOptionCode() != null && !request.getModelOptionCode().isBlank()) {
            params.put("model_option_code", request.getModelOptionCode().trim());
        }

        log.debug("Summarize content length: {}, type: {}",
                request.getContent() != null ? request.getContent().length() : 0,
                request.getSummaryType());

        return executeStreamTask(userId, AiTaskType.CHAT_SUMMARY, params, "summary");
    }

    /**
     * 执行日程提取任务（同步返回结构化结果）。
     *
     * @param userId  用户 ID
     * @param request 日程提取请求
     * @return 日程提取结果
     */
    @Override
    public ScheduleExtractionVO extractSchedule(Long userId, ScheduleRequest request) {
        log.info("Schedule extraction request from user: {}", userId);

        Map<String, Object> params = new HashMap<>();
        params.put("messages", request.getMessages());
        if (request.getModelOptionCode() != null && !request.getModelOptionCode().isBlank()) {
            params.put("model_option_code", request.getModelOptionCode().trim());
        }

        AiTask task = aiTaskService.createTask(userId, AiTaskType.SCHEDULE_EXTRACTION, params);

        try {
            aiTaskService.updateTaskStatus(task.getId(), AiTaskStatus.PROCESSING);

            String result = aiFacadeHandler.executeTaskWithTools(userId, AiTaskType.SCHEDULE_EXTRACTION, params, AiProvider.QWEN);
            log.debug("AI schedule extraction result: {}", result);

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("raw_result", result);

            ScheduleExtractionVO vo;
            try {
                String cleanResult = result.replaceAll("```json", "").replaceAll("```", "").trim();
                vo = JsonUtil.fromJson(cleanResult, ScheduleExtractionVO.class);
                resultData.put("schedules", vo);
            } catch (Exception e) {
                log.warn("Failed to parse AI result to VO", e);
                throw new JsonException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "解析AI结果失败");
            }

            aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.COMPLETED, resultData, null);
            return vo;

        } catch (Exception e) {
            log.error("Schedule extraction failed", e);
            aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.FAILED, null, e.getMessage());
            throw new RuntimeException("AI schedule extraction failed", e);
        }
    }

    /**
     * 基于时间线消息发起异步人格分析任务。
     *
     * @param userId 用户 ID
     */
    @Override
    public void analyzePersonaFromTimeline(Long userId) {
        if (!aiExternalService.isAiAnalysisEnabled(userId)) {
            log.debug("Skip persona analysis because user {} has disabled analysis", userId);
            return;
        }

        String pendingKey = PERSONA_TIMELINE_COUNTER_KEY_PREFIX + userId;
        int pendingCount = readPendingCount(pendingKey);

        OffsetDateTime triggerTime = readTriggerTime(userId);
        OffsetDateTime lastAnalyzedAt = getLastAnalyzedAt(userId);

        int fetchLimit = Math.min(Math.max(pendingCount * 3, pendingCount + 20), PERSONA_TIMELINE_FETCH_MAX);
        List<MessageItem> messages = selectIncrementalMessages(userId, fetchLimit, pendingCount, lastAnalyzedAt, triggerTime);
        if (messages.isEmpty()) {
            log.info("Skip persona analysis because user {} has no incremental messages in window", userId);
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("messages", messages);
        params.put("target_user_id", userId.toString());

        AiTask task = aiTaskService.createTask(userId, AiTaskType.PERSONA_ANALYSIS, params);
        aiTaskService.updateTaskStatus(task.getId(), AiTaskStatus.PROCESSING);

        Mono.fromCallable(() -> aiFacadeHandler.executeTask(userId, AiTaskType.PERSONA_ANALYSIS, params, AiProvider.QWEN))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(result -> {
                            try {
                                String jsonStr = extractJson(result);
                                Map<String, Object> resultData = new HashMap<>();
                                try {
                                    PersonaAnalysisResultVO resultVO = JsonUtil.fromJson(jsonStr, PersonaAnalysisResultVO.class);
                                    resultData.put("analysis", resultVO);
                                } catch (Exception e) {
                                    resultData.put("analysis", result);
                                }

                                aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.COMPLETED, resultData, null);
                                savePersonaAnalysis(userId, result, messages);
                                consumePendingMessages(userId, messages.size());

                                log.info("Timeline persona analysis completed for user: {}, task: {}", userId, task.getPublicId());
                            } catch (Exception e) {
                                log.error("Failed to persist timeline persona analysis result for user {}", userId, e);
                            }
                        },
                        error -> {
                            log.error("Timeline persona analysis failed for user {}", userId, error);
                            try {
                                aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.FAILED, null, error.getMessage());
                            } catch (Exception e) {
                                log.error("Failed to update persona task status to FAILED for user {}", userId, e);
                            }
                        }
                );
    }

    /**
     * 读取待分析计数。
     *
     * @param pendingKey Redis 计数键
     * @return 待分析消息数量，解析失败时返回 0
     */
    private int readPendingCount(String pendingKey) {
        String value = stringRedisTemplate.opsForValue().get(pendingKey);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(value), 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 读取最近触发时间；如果不存在则使用当前时间。
     *
     * @param userId 用户 ID
     * @return 最近触发时间
     */
    private OffsetDateTime readTriggerTime(Long userId) {
        String triggerKey = PERSONA_TIMELINE_LAST_TRIGGER_KEY_PREFIX + userId;
        String value = stringRedisTemplate.opsForValue().get(triggerKey);
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            long epochSeconds = Long.parseLong(value);
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            return OffsetDateTime.now();
        }
    }

    /**
     * 查询当前画像最近分析时间，用于增量窗口起点。
     *
     * @param userId 用户 ID
     * @return 最近分析时间，若不存在画像则返回 null
     */
    private OffsetDateTime getLastAnalyzedAt(Long userId) {
        UserAiProfile profile = userAiProfileMapper.selectOne(new LambdaQueryWrapper<UserAiProfile>()
                .eq(UserAiProfile::getUserId, userId)
                .eq(UserAiProfile::getProfileType, "PERSONA"));
        return profile == null ? null : profile.getLastAnalyzedAt();
    }

    /**
     * 在触发窗口内挑选增量消息，优先选取 lastAnalyzedAt 之后且 triggerTime 之前的消息。
     *
     * @param userId         用户 ID
     * @param fetchLimit     拉取候选消息上限
     * @param pendingCount   待消费的消息数量
     * @param lastAnalyzedAt 上次画像分析时间
     * @param triggerTime    本次触发时间
     * @return 供本次画像分析使用的增量消息列表
     */
    private List<MessageItem> selectIncrementalMessages(Long userId,
                                                        int fetchLimit,
                                                        int pendingCount,
                                                        OffsetDateTime lastAnalyzedAt,
                                                        OffsetDateTime triggerTime) {
        List<MessageItem> all = toInternalMessages(aiExternalService.getUserRecentMessagesForAnalysis(userId, fetchLimit));
        if (all.isEmpty()) {
            return List.of();
        }

        List<MessageItem> filtered = all.stream()
                .filter(Objects::nonNull)
                .filter(item -> isInTimelineWindow(item, lastAnalyzedAt, triggerTime))
                .sorted(Comparator.comparing(this::safeTimestamp))
                .toList();

        if (filtered.isEmpty()) {
            filtered = all.stream().filter(Objects::nonNull).toList();
        }

        int size = filtered.size();
        int startIndex = Math.max(size - pendingCount, 0);
        return filtered.subList(startIndex, size);
    }

    /**
     * 判断消息是否位于本次增量分析窗口。
     *
     * @param item           消息对象
     * @param lastAnalyzedAt 上次画像分析时间
     * @param triggerTime    本次触发时间
     * @return true 表示消息位于增量窗口内
     */
    private boolean isInTimelineWindow(MessageItem item, OffsetDateTime lastAnalyzedAt, OffsetDateTime triggerTime) {
        OffsetDateTime ts = safeTimestamp(item);
        if (ts == null) {
            return true;
        }
        if (lastAnalyzedAt != null && !ts.isAfter(lastAnalyzedAt)) {
            return false;
        }
        return triggerTime == null || !ts.isAfter(triggerTime);
    }

    /**
     * 解析消息时间戳；不可解析时返回 null。
     *
     * @param item 消息对象
     * @return 消息时间戳，无法解析时返回 null
     */
    private OffsetDateTime safeTimestamp(MessageItem item) {
        if (item == null || item.getTimestamp() == null || item.getTimestamp().isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(item.getTimestamp());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 分析成功后扣减已消费的 pending 计数，保留并发期间新增的未消费计数。
     *
     * @param userId   用户 ID
     * @param consumed 本次已消费消息数量
     */
    private void consumePendingMessages(Long userId, int consumed) {
        if (consumed <= 0) {
            return;
        }
        String pendingKey = PERSONA_TIMELINE_COUNTER_KEY_PREFIX + userId;
        Long remain = stringRedisTemplate.opsForValue().increment(pendingKey, -consumed);
        if (remain == null || remain < 0) {
            stringRedisTemplate.opsForValue().set(pendingKey, "0");
        }
    }

    /**
     * 禁用用户画像（删除 PERSONA 画像记录）。
     *
     * @param userId 用户 ID
     */
    @Override
    public void disablePersona(Long userId) {
        log.info("Disabling persona for user: {}", userId);
        // 逻辑：删除或标记画像为不可用。这里选择删除画像记录。
        userAiProfileMapper.delete(new LambdaQueryWrapper<UserAiProfile>()
                .eq(UserAiProfile::getUserId, userId)
                .eq(UserAiProfile::getProfileType, "PERSONA"));
    }

    /**
     * 保存人格分析结果到用户画像表。
     *
     * @param userId         用户 ID
     * @param analysisResult 原始分析结果文本
     * @param sourceMessages 样本消息集合
     */
    private void savePersonaAnalysis(Long userId, String analysisResult, List<MessageItem> sourceMessages) {
        PersonaAnalysisResultVO parsedResult;
        try {
            String jsonStr = extractJson(analysisResult);
            parsedResult = JsonUtil.fromJson(jsonStr, PersonaAnalysisResultVO.class);
        } catch (Exception e) {
            log.warn("Failed to parse persona analysis, saving as null with raw fallback", e);
            parsedResult = null;
        }

        LambdaQueryWrapper<UserAiProfile> wrapper = new LambdaQueryWrapper<UserAiProfile>()
                .eq(UserAiProfile::getUserId, userId)
                .eq(UserAiProfile::getProfileType, "PERSONA");

        UserAiProfile profile = userAiProfileMapper.selectOne(wrapper);

        if (profile == null) {
            profile = new UserAiProfile();
            profile.setUserId(userId);
            profile.setProfileType("PERSONA");
            profile.setProvider(AiProvider.QWEN.name());
            profile.setModelName("qwen-max");
            profile.setVersion(1);
            profile.setCreatedAt(OffsetDateTime.now());
        } else {
            profile.setVersion(profile.getVersion() + 1);
        }

        profile.setContent(parsedResult);
        profile.setConfidence(resolveConfidence(parsedResult));
        profile.setSourceMessageCount(sourceMessages != null ? sourceMessages.size() : 0);
        profile.setSourceTimeFrom(resolveSourceTimeBoundary(sourceMessages, true));
        profile.setSourceTimeTo(resolveSourceTimeBoundary(sourceMessages, false));
        profile.setLastAnalyzedAt(OffsetDateTime.now());
        profile.setUpdatedAt(OffsetDateTime.now());

        if (profile.getId() == null) {
            userAiProfileMapper.insert(profile);
        } else {
            userAiProfileMapper.updateById(profile);
        }

        log.info("Saved persona analysis for user: {}", userId);
    }

    /**
     * 规范化置信度到 [0, 1] 区间。
     *
     * @param result 人格分析结果对象
     * @return 归一化后的置信度
     */
    private BigDecimal resolveConfidence(PersonaAnalysisResultVO result) {
        if (result == null || result.getConfidence() == null) {
            return null;
        }
        BigDecimal value = result.getConfidence();
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return value;
    }

    /**
     * 解析样本消息时间边界。
     *
     * @param sourceMessages 样本消息列表
     * @param isMin          true 取最小时间，false 取最大时间
     * @return 时间边界，无法解析时返回 null
     */
    private OffsetDateTime resolveSourceTimeBoundary(List<MessageItem> sourceMessages, boolean isMin) {
        if (sourceMessages == null || sourceMessages.isEmpty()) {
            return null;
        }

        OffsetDateTime boundary = null;
        for (MessageItem item : sourceMessages) {
            if (item == null || item.getTimestamp() == null || item.getTimestamp().isBlank()) {
                continue;
            }
            try {
                OffsetDateTime current = OffsetDateTime.parse(item.getTimestamp());
                if (boundary == null) {
                    boundary = current;
                } else if (isMin && current.isBefore(boundary)) {
                    boundary = current;
                } else if (!isMin && current.isAfter(boundary)) {
                    boundary = current;
                }
            } catch (Exception ignore) {
                // 忽略无法解析的时间戳，继续处理其余样本。
            }
        }

        return boundary;
    }

    /**
     * 将外部消息对象转换为内部消息结构。
     *
     * @param externalItems 外部消息列表
     * @return 内部消息列表
     */
    private List<MessageItem> toInternalMessages(List<AiExternalMessageItem> externalItems) {
        if (externalItems == null || externalItems.isEmpty()) {
            return List.of();
        }

        return externalItems.stream().map(item -> {
            MessageItem message = new MessageItem();
            message.setSender(item.getSender());
            message.setContent(item.getContent());
            message.setTimestamp(item.getTimestamp());
            return message;
        }).collect(Collectors.toList());
    }

    /**
     * 从文本中提取 JSON 主体内容。
     *
     * @param text 原始文本
     * @return 提取后的 JSON 字符串
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return "{}";
        }

        // 移除Markdown代码块标记
        String cleanText = text.replaceAll("```json", "").replaceAll("```", "").trim();

        int start = cleanText.indexOf("{");
        int end = cleanText.lastIndexOf("}");

        if (start != -1 && end != -1 && end > start) {
            return cleanText.substring(start, end + 1);
        }

        return cleanText;
    }

    /**
     * 通用流式任务执行模板：创建任务、执行流式输出、写入任务结果。
     *
     * @param userId    用户 ID
     * @param taskType  任务类型
     * @param params    输入参数
     * @param resultKey 结果写入键
     * @return 流式输出
     */
    private Flux<StreamOutputVO> executeStreamTask(Long userId, AiTaskType taskType, Map<String, Object> params, String resultKey) {
        return Flux.defer(() -> {
            AiTask task = aiTaskService.createTask(userId, taskType, params);
            aiTaskService.updateTaskStatus(task.getId(), AiTaskStatus.PROCESSING);

            StringBuilder fullContent = new StringBuilder();

            return aiFacadeHandler
                    .executeTaskStream(userId, taskType, params, AiProvider.QWEN)
                    .map(chunk -> {
                        fullContent.append(chunk);
                        return new StreamOutputVO(chunk);
                    })
                    .doOnComplete(() -> {
                        try {
                            Map<String, Object> resultData = new HashMap<>();
                            resultData.put(resultKey, fullContent.toString());
                            aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.COMPLETED, resultData, null);
                        } catch (Exception e) {
                            log.error("Failed to update task result", e);
                        }
                    })
                    .doOnError(e -> {
                        try {
                            aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.FAILED, null, e.getMessage());
                        } catch (Exception ex) {
                            log.error("Failed to update task status to FAILED", ex);
                        }
                    });
        });
    }
}
