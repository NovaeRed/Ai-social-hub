package cn.redture.aiEngine.service.impl;

import cn.redture.aiEngine.handler.AiFacadeHandler;
import cn.redture.aiEngine.model.core.routing.ModelRouteDecision;
import cn.redture.aiEngine.mapper.AiTaskMapper;
import cn.redture.aiEngine.mapper.UserAiProfileMapper;
import cn.redture.aiEngine.pojo.dto.MessageItem;
import cn.redture.aiEngine.pojo.dto.NotificationAsyncTaskDTO;
import cn.redture.aiEngine.pojo.entity.AiTask;
import cn.redture.aiEngine.pojo.entity.UserAiProfile;
import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.model.ModelConfig;
import cn.redture.aiEngine.pojo.vo.PersonaAnalysisResultVO;
import cn.redture.aiEngine.service.AiTaskExecutionService;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.util.JsonUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_KEY;

/**
 * AI 异步任务执行服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskExecutionServiceImpl implements AiTaskExecutionService {

    private static final String PERSONA_TIMELINE_COUNTER_KEY_PREFIX = "ai:persona:timeline:pending:";

    private final AiTaskService aiTaskService;
    private final AiTaskMapper aiTaskMapper;
    private final AiFacadeHandler aiFacadeHandler;
    private final UserAiProfileMapper userAiProfileMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void executeQueuedAiTask(Long userId, Long aiTaskId) {
        if (userId == null || aiTaskId == null) {
            return;
        }

        AiTask task = aiTaskMapper.selectById(aiTaskId);
        if (task == null || !Objects.equals(task.getUserId(), userId)) {
            log.warn("Skip queued AI task because task not found or user mismatch, userId={}, aiTaskId={}", userId, aiTaskId);
            return;
        }

        if (task.getTaskStatus() == AiTaskStatus.COMPLETED || task.getTaskStatus() == AiTaskStatus.FAILED) {
            log.debug("Skip queued AI task because it is already terminal, aiTaskId={}, status={}", aiTaskId, task.getTaskStatus());
            return;
        }

        if (task.getTaskType() != AiTaskType.PERSONA_ANALYSIS) {
            log.debug("Skip queued AI task because task type {} is not handled yet, aiTaskId={}", task.getTaskType(), aiTaskId);
            return;
        }

        Map<String, Object> params = task.getInputPayload() == null ? new HashMap<>() : new HashMap<>(task.getInputPayload());
        List<MessageItem> messages = extractMessageItems(params.get("messages"));

        try {
            ModelRouteDecision route = aiFacadeHandler.resolveSystemDefaultRoute(task.getTaskType());
            attachRequestedModelOptionCode(params, route);
            persistTaskRouting(aiTaskId, params, route);

            aiTaskService.updateTaskStatus(aiTaskId, AiTaskStatus.PROCESSING);
            String result = aiFacadeHandler.executeTask(userId, task.getTaskType(), params, route);

            String jsonStr = extractJson(result);
            Map<String, Object> resultData = new HashMap<>();
            try {
                PersonaAnalysisResultVO resultVO = JsonUtil.fromJson(jsonStr, PersonaAnalysisResultVO.class);
                resultData.put("analysis", resultVO);
            } catch (Exception e) {
                resultData.put("analysis", result);
            }

            aiTaskService.updateTaskResult(aiTaskId, AiTaskStatus.COMPLETED, resultData, null);
            savePersonaAnalysis(userId, result, messages, route);
            consumePendingMessages(userId, messages.size());
            enqueueNotificationTask(userId, task);

            log.info("Queued AI task executed successfully, aiTaskId={}, publicId={}", aiTaskId, task.getPublicId());
        } catch (Exception e) {
            log.error("Queued AI task execution failed, aiTaskId={}", aiTaskId, e);
            aiTaskService.updateTaskResult(aiTaskId, AiTaskStatus.FAILED, null, e.getMessage());
        }
    }

    @Override
    public void failQueuedAiTask(Long userId, Long aiTaskId, String errorCode, String errorMessage) {
        if (userId == null || aiTaskId == null) {
            return;
        }

        AiTask task = aiTaskMapper.selectById(aiTaskId);
        if (task == null || !Objects.equals(task.getUserId(), userId)) {
            return;
        }

        if (task.getTaskStatus() == AiTaskStatus.COMPLETED || task.getTaskStatus() == AiTaskStatus.FAILED) {
            return;
        }

        String code = (errorCode == null || errorCode.isBlank()) ? ErrorCodes.AI_TASK_DLQ : errorCode;
        String message = (errorMessage == null || errorMessage.isBlank()) ? "AI task reached max retries" : errorMessage;
        aiTaskService.updateTaskResult(aiTaskId, AiTaskStatus.FAILED, null, "[" + code + "] " + message);
    }

    /**
     * 从任务输入中解析消息列表。
     *
     * @param source 原始消息对象
     * @return 解析后的消息列表
     */
    private List<MessageItem> extractMessageItems(Object source) {
        if (source == null) {
            return List.of();
        }
        try {
            String json = JsonUtil.toJson(source);
            MessageItem[] items = JsonUtil.fromJson(json, MessageItem[].class);
            if (items == null || items.length == 0) {
                return List.of();
            }
            return Arrays.asList(items);
        } catch (Exception e) {
            log.warn("Failed to parse message items from task input payload", e);
            return List.of();
        }
    }

    /**
     * 投递任务完成通知。
     *
     * @param userId 用户 ID
     * @param aiTask AI 任务实体
     */
    private void enqueueNotificationTask(Long userId, AiTask aiTask) {
        NotificationAsyncTaskDTO notification = new NotificationAsyncTaskDTO();
        notification.setUserId(userId);
        notification.setEventType("AI_TASK_COMPLETED");

        Map<String, Object> payloadBody = new HashMap<>();
        payloadBody.put("ai_task_id", aiTask.getId());
        payloadBody.put("task_public_id", aiTask.getPublicId());
        payloadBody.put("task_type", aiTask.getTaskType() == null ? null : aiTask.getTaskType().name());
        payloadBody.put("status", AiTaskStatus.COMPLETED.name());
        notification.setPayload(payloadBody);

        Map<String, String> payload = new HashMap<>();
        payload.put("domain", AsyncTaskDomain.NOTIFICATION_TASK.name());
        payload.put("task", JsonUtil.toJson(notification));
        payload.put("biz_id", "notify:" + userId + ":" + aiTask.getId());
        payload.put("trace_id", UUID.randomUUID().toString());
        payload.put("user_id", String.valueOf(userId));
        payload.put("created_at_epoch", String.valueOf(OffsetDateTime.now().toEpochSecond()));
        payload.put("retry_count", "0");
        stringRedisTemplate.opsForStream().add(StreamRecords.string(payload).withStreamKey(AI_ASYNC_TASK_STREAM_KEY));
    }

    /**
     * 扣减用户待分析计数。
     *
     * @param userId   用户 ID
     * @param consumed 已消费消息数
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
     * 保存画像分析结果。
     *
     * @param userId         用户 ID
     * @param analysisResult 原始分析结果
     * @param sourceMessages 样本消息
     * @param route          模型路由决策
     */
    private void savePersonaAnalysis(Long userId,
                                     String analysisResult,
                                     List<MessageItem> sourceMessages,
                                     ModelRouteDecision route) {
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
            profile.setProvider(route == null ? null : route.resolvedProvider());
            profile.setModelName(route == null ? null : route.resolvedModelName());
            profile.setVersion(1);
            profile.setCreatedAt(OffsetDateTime.now());
        } else {
            profile.setVersion(profile.getVersion() + 1);
            profile.setProvider(route == null ? profile.getProvider() : route.resolvedProvider());
            profile.setModelName(route == null ? profile.getModelName() : route.resolvedModelName());
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
     * 确保任务输入中包含用于审计展示的请求模型编码。
     *
     * @param params 任务参数
     * @param route  模型路由决策
     */
    private void attachRequestedModelOptionCode(Map<String, Object> params, ModelRouteDecision route) {
        if (params == null || route == null) {
            return;
        }
        Object value = params.get("model_option_code");
        if (value == null || String.valueOf(value).isBlank()) {
            params.put("model_option_code", route.requestedModelOptionCode());
        }
    }

    /**
     * 将模型路由结果持久化到任务记录，便于后续查询展示。
     *
     * @param taskId       任务主键 ID
     * @param inputPayload 任务输入参数
     * @param route        模型路由决策
     */
    private void persistTaskRouting(Long taskId, Map<String, Object> inputPayload, ModelRouteDecision route) {
        if (taskId == null || route == null) {
            return;
        }
        AiTask patch = new AiTask();
        patch.setId(taskId);
        patch.setInputPayload(inputPayload);
        patch.setProvider(route.resolvedProvider());
        patch.setModelConfig(new ModelConfig(route.resolvedModelName(), null, null, null, null, null));
        aiTaskMapper.updateById(patch);
    }

    /**
     * 归一化置信度到 [0, 1] 区间。
     *
     * @param result 画像分析结果
     * @return 归一化置信度
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
     * @param sourceMessages 样本消息
     * @param isMin          true 表示取最早时间，false 表示取最晚时间
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
     * 从文本中提取 JSON 主体。
     *
     * @param text 原始文本
     * @return JSON 文本
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return "{}";
        }

        String cleanText = text.replaceAll("```json", "").replaceAll("```", "").trim();
        int start = cleanText.indexOf("{");
        int end = cleanText.lastIndexOf("}");

        if (start != -1 && end != -1 && end > start) {
            return cleanText.substring(start, end + 1);
        }

        return cleanText;
    }
}
