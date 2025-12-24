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
import cn.redture.aiEngine.service.AiExternalService;
import cn.redture.aiEngine.service.AiInteractionService;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.exception.JsonException;
import cn.redture.common.util.JsonUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    @Override
    public Flux<StreamOutputVO> polishStream(Long userId, PolishRequest request) {
        log.info("Polish request from user: {}", userId);
        Map<String, Object> params = new HashMap<>();
        params.put("message", request.getMessage());
        if (request.getOverrideConfig() != null) {
            params.put("override_config", request.getOverrideConfig());
        }
        return executeStreamTask(userId, AiTaskType.POLISH, params, "polished_message");
    }

    @Override
    public Flux<StreamOutputVO> translateStream(Long userId, TranslationRequest request) {
        log.info("Translation request from user: {}", userId);
        Map<String, Object> params = new HashMap<>();
        params.put("text", request.getText());
        params.put("targetLanguage", request.getTargetLanguage());
        if (request.getDomain() != null) {
            params.put("domain", request.getDomain());
        }
        if (request.getOverrideConfig() != null) {
            params.put("override_config", request.getOverrideConfig());
        }
        return executeStreamTask(userId, AiTaskType.TRANSLATION, params, "translation");
    }

    @Override
    public Flux<StreamOutputVO> smartReplyStream(Long userId, SmartReplyRequest request) {
        log.info("Smart reply request from user: {}", userId);
        Map<String, Object> params = new HashMap<>();
        params.put("message", request.getMessage());
        params.put("conversation_history", request.getConversationHistory());
        if (request.getUserProfile() != null) {
            params.put("user_profile", request.getUserProfile());
        }
        if (request.getOverrideConfig() != null) {
            params.put("override_config", request.getOverrideConfig());
        }
        return executeStreamTask(userId, AiTaskType.SMART_REPLY, params, "reply");
    }

    @Override
    public Flux<StreamOutputVO> summarizeStream(Long userId, SummarizeRequest request) {
        log.info("Summarize request from user: {}", userId);
        Map<String, Object> params = new HashMap<>();
        params.put("content", request.getContent());
        params.put("summary_type", request.getSummaryType());
        params.put("target_length", request.getTargetLength());
        if (request.getKeywords() != null) {
            params.put("keywords", request.getKeywords());
        }
        if (request.getOverrideConfig() != null) {
            params.put("override_config", request.getOverrideConfig());
        }
        return executeStreamTask(userId, AiTaskType.CHAT_SUMMARY, params, "summary");
    }

    @Override
    public ScheduleExtractionVO extractSchedule(Long userId, ScheduleRequest request) {
        log.info("Schedule extraction request from user: {}", userId);

        Map<String, Object> params = new HashMap<>();
        params.put("messages", request.getMessages());
        if (request.getOverrideConfig() != null) {
            params.put("override_config", request.getOverrideConfig());
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

    @Override
    public PersonaAnalysisVO analyzePersonaAsync(Long userId, PersonaAnalysisRequest request) {
        // 检查用户是否开启了 AI 画像分析
        if (!aiExternalService.isAiAnalysisEnabled(userId)) {
            log.warn("User {} has not enabled AI persona analysis", userId);
            throw new JsonException(HttpStatus.FORBIDDEN.value(), "请先在设置中开启 AI 画像分析功能");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("messages", request.getMessages());
        params.put("target_user_id", request.getTargetUserId());
        if (request.getAnalysisConfig() != null) {
            params.put("analysis_config", request.getAnalysisConfig());
        }
        if (request.getOverrideConfig() != null) {
            params.put("override_config", request.getOverrideConfig());
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
                                savePersonaAnalysis(userId, result);
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

        return PersonaAnalysisVO.builder()
                .taskPublicId(task.getPublicId())
                .build();
    }

    @Override
    public void initPersona(Long userId) {
        log.info("Initializing persona for user: {}", userId);

        // 1. 获取用户最近的聊天记录 (最近 50 条)
        List<MessageItem> messageItems = aiExternalService.getUserRecentMessages(userId, 50);

        if (messageItems.isEmpty()) {
            log.warn("No messages found for user {}, skipping persona initialization", userId);
            return;
        }

        // 2. 构建分析参数
        Map<String, Object> params = new HashMap<>();
        params.put("messages", messageItems);
        params.put("target_user_id", userId.toString());

        // 3. 执行同步分析
        try {
            String result = aiFacadeHandler.executeTask(userId, AiTaskType.PERSONA_ANALYSIS, params, AiProvider.QWEN);
            savePersonaAnalysis(userId, result);
            log.info("Successfully initialized persona for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to initialize persona for user: {}", userId, e);
        }
    }

    @Override
    public void disablePersona(Long userId) {
        log.info("Disabling persona for user: {}", userId);
        // 逻辑：删除或标记画像为不可用。这里选择删除画像记录。
        userAiProfileMapper.delete(new LambdaQueryWrapper<UserAiProfile>()
                .eq(UserAiProfile::getUserId, userId)
                .eq(UserAiProfile::getProfileType, "PERSONA"));
    }

    private void savePersonaAnalysis(Long userId, String analysisResult) {
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
        profile.setUpdatedAt(OffsetDateTime.now());

        if (profile.getId() == null) {
            userAiProfileMapper.insert(profile);
        } else {
            userAiProfileMapper.updateById(profile);
        }

        log.info("Saved persona analysis for user: {}", userId);
    }

    /**
     * 从文本中提取JSON部分
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
     * 通用流式任务执行方法
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
