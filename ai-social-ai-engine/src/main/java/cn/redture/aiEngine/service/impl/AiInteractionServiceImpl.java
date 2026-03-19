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
import cn.redture.aiEngine.pojo.vo.PersonaAnalysisVO;
import cn.redture.aiEngine.pojo.vo.ScheduleExtractionVO;
import cn.redture.aiEngine.pojo.vo.StreamOutputVO;
import cn.redture.aiEngine.service.AiInteractionService;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.exception.JsonException;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.integration.ai.dto.AiExternalMessageItem;
import cn.redture.common.util.JsonUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI交互服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInteractionServiceImpl implements AiInteractionService {

    private final AiTaskService aiTaskService;
    private final AiFacadeHandler aiFacadeHandler;
    private final UserAiProfileMapper userAiProfileMapper;
    private final AiExternalService aiExternalService;

    /**
     * 执行文本润色任务（流式返回）。
     *
     * @param userId 用户 ID
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
     * @param userId 用户 ID
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
     * @param userId 用户 ID
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
     * @param userId 用户 ID
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
     * @param userId 用户 ID
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
     * 发起异步人格分析任务并返回任务 ID。
     *
     * @param userId 用户 ID
     * @param request 人格分析请求
     * @return 异步任务信息
     */
    @Override
    public PersonaAnalysisVO analyzePersonaAsync(Long userId, PersonaAnalysisRequest request) {
        // 检查用户是否开启了 AI 画像分析
        if (!aiExternalService.isAiAnalysisEnabled(userId)) {
            log.warn("User {} has not enabled AI persona analysis", userId);
            throw new JsonException(HttpStatus.FORBIDDEN.value(), "请先在设置中开启 AI 画像分析功能");
        }

        Map<String, Object> params = new HashMap<>();

        // 支持两种方式提供消息：
        // 1. 显式传 messages（两步式）
        // 2. 传 selected_message_ids 或走后端自动兜底（一体化）
        List<MessageItem> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            if (request.getSelectedMessageIds() != null && !request.getSelectedMessageIds().isEmpty()) {
                messages = toInternalMessages(aiExternalService.getMessagesByIds(request.getSelectedMessageIds()));
                log.debug("Resolved {} messages from selected_message_ids", messages.size());
            } else {
                messages = toInternalMessages(aiExternalService.getUserRecentMessagesForAnalysis(userId, 50));
                log.debug("Resolved {} recent messages for persona analysis", messages.size());
            }
        }

        if (messages.isEmpty()) {
            throw new InvalidInputException("缺少可分析的消息内容");
        }

        final List<MessageItem> finalMessages = messages;
        params.put("messages", finalMessages);
        params.put("target_user_id", request.getTargetUserId() != null ? request.getTargetUserId() : userId.toString());

        if (request.getAnalysisConfig() != null) {
            params.put("analysis_config", request.getAnalysisConfig());
        }
        if (request.getModelOptionCode() != null && !request.getModelOptionCode().isBlank()) {
            params.put("model_option_code", request.getModelOptionCode().trim());
        }

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

                                // 保存性格分析结果到用户画像
                                savePersonaAnalysis(userId, result, finalMessages);

                                log.info("Persona analysis completed for user: {}, task: {}", userId, task.getPublicId());
                            } catch (Exception e) {
                                log.error("Failed to update task result", e);
                            }
                        },
                        error -> {
                            log.error("Persona analysis failed", error);
                            try {
                                aiTaskService.updateTaskResult(task.getId(), AiTaskStatus.FAILED, null, error.getMessage());
                            } catch (Exception e) {
                                log.error("Failed to update task status", e);
                            }
                        }
                );

        PersonaAnalysisVO vo = new PersonaAnalysisVO();
        vo.setTaskPublicId(task.getPublicId());
        return vo;
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
     * @param userId 用户 ID
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
     * @param isMin true 取最小时间，false 取最大时间
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
        * @param userId 用户 ID
        * @param taskType 任务类型
        * @param params 输入参数
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
