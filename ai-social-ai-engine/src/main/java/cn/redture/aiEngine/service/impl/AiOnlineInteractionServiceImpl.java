package cn.redture.aiEngine.service.impl;

import cn.redture.aiEngine.facade.orchestrator.AiTaskOrchestrator;
import cn.redture.aiEngine.mapper.AiTaskMapper;
import cn.redture.aiEngine.pojo.dto.PolishRequest;
import cn.redture.aiEngine.pojo.dto.ScheduleRequest;
import cn.redture.aiEngine.pojo.dto.SmartReplyRequest;
import cn.redture.aiEngine.pojo.dto.SummarizeRequest;
import cn.redture.aiEngine.pojo.dto.TranslationRequest;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.vo.ScheduleExtractionVO;
import cn.redture.aiEngine.pojo.vo.StreamOutputVO;
import cn.redture.aiEngine.service.AiOnlineInteractionService;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.exception.JsonException;
import cn.redture.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 在线交互服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOnlineInteractionServiceImpl implements AiOnlineInteractionService {

    private final AiTaskOrchestrator aiTaskOrchestrator;

    /**
     * 执行文本润色流式任务
     *
     * @param userId  用户 ID
     * @param request 润色请求
     * @return 流式输出
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
     * 执行翻译流式任务
     *
     * @param userId  用户 ID
     * @param request 翻译请求
     * @return 流式输出
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
     * 执行智能回复流式任务
     *
     * @param userId  用户 ID
     * @param request 智能回复请求
     * @return 流式输出
     */
    @Override
    public Flux<StreamOutputVO> smartReplyStream(Long userId, SmartReplyRequest request) {
        log.info("Smart reply request from user: {}, conversation: {}", userId, request.getConversationPublicId());

        Map<String, Object> params = new HashMap<>();
        params.put("message", request.getMessage());

        if (request.getConversationHistory() != null && !request.getConversationHistory().isEmpty()) {
            params.put("conversation_history", request.getConversationHistory());
            log.debug("Using provided conversation history with {} messages", request.getConversationHistory().size());
        } else if (request.getConversationPublicId() != null) {
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
     * 执行内容总结流式任务
     *
     * @param userId  用户 ID
     * @param request 总结请求
     * @return 流式输出
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

        return executeStreamTask(userId, AiTaskType.CHAT_SUMMARY, params, "summary");
    }

    /**
     * 执行日程提取同步任务
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

        return aiTaskOrchestrator.submitAndExecuteSync(
                userId,
                AiTaskType.SCHEDULE_EXTRACTION,
                params,
                rawResult -> {
                    String cleanResult = rawResult.replaceAll("```json", "").replaceAll("```", "").trim();
                    try {
                        return JsonUtil.fromJson(cleanResult, ScheduleExtractionVO.class);
                    } catch (Exception e) {
                        throw new JsonException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "解析AI结果失败: " + e.getMessage());
                    }
                }
        );
    }

    /**
     * 通用流式任务执行模板
     *
     * @param userId    用户 ID
     * @param taskType  任务类型
     * @param params    输入参数
     * @param resultKey 输出结果键
     * @return 流式输出
     */
    private Flux<StreamOutputVO> executeStreamTask(Long userId, AiTaskType taskType, Map<String, Object> params, String resultKey) {
        return aiTaskOrchestrator.submitAndExecuteStream(userId, taskType, params, resultKey);
    }
}
