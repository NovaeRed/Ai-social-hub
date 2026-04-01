package cn.redture.aiEngine.service.impl;

import cn.redture.aiEngine.producer.StreamMessagePublisher;
import cn.redture.common.event.MessageEnvelope;
import cn.redture.aiEngine.mapper.UserAiProfileMapper;
import cn.redture.aiEngine.pojo.dto.AiAsyncTaskDTO;
import cn.redture.aiEngine.pojo.dto.MessageItem;
import cn.redture.aiEngine.pojo.entity.AiTask;
import cn.redture.aiEngine.pojo.entity.UserAiProfile;
import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.service.AiAsyncSubmissionService;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.integration.ai.dto.AiExternalMessageItem;
import cn.redture.common.util.JsonUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.redture.common.constants.RedisConstants.AI_ASYNC_TASK_STREAM_KEY;

/**
 * AI 异步任务提交服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAsyncSubmissionServiceImpl implements AiAsyncSubmissionService {

    private static final String PERSONA_TIMELINE_COUNTER_KEY_PREFIX = "ai:persona:timeline:pending:";
    private static final String PERSONA_TIMELINE_LAST_TRIGGER_KEY_PREFIX = "ai:persona:timeline:last-trigger:";
    private static final int PERSONA_TIMELINE_FETCH_MAX = 500;

    private final AiTaskService aiTaskService;
    private final UserAiProfileMapper userAiProfileMapper;
    private final StreamMessagePublisher streamMessagePublisher;
    private final AiExternalService aiExternalService;
    private final StringRedisTemplate stringRedisTemplate;

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
        enqueueAiTaskExecution(userId, task);
        log.info("Timeline persona analysis enqueued to async bus, userId={}, taskId={}, publicId={}",
                userId, task.getId(), task.getPublicId());
    }

    private void enqueueAiTaskExecution(Long userId, AiTask task) {
        AiAsyncTaskDTO asyncTask = new AiAsyncTaskDTO();
        asyncTask.setUserId(userId);
        asyncTask.setAiTaskId(task.getId());
        asyncTask.setTaskType(task.getTaskType().name());

        MessageEnvelope<AiAsyncTaskDTO> envelope = MessageEnvelope.<AiAsyncTaskDTO>builder()
                .domain("PERSONA_TASK")
                .eventType(task.getTaskType().name())
                .userId(userId)
                .bizId("ai-task:" + task.getId())
                .payload(asyncTask)
                .build();

        streamMessagePublisher.publish("stream:ai-async-tasks", envelope);
    }

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

    private OffsetDateTime getLastAnalyzedAt(Long userId) {
        UserAiProfile profile = userAiProfileMapper.selectOne(new LambdaQueryWrapper<UserAiProfile>()
                .eq(UserAiProfile::getUserId, userId)
                .eq(UserAiProfile::getProfileType, "PERSONA"));
        return profile == null ? null : profile.getLastAnalyzedAt();
    }

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
}
