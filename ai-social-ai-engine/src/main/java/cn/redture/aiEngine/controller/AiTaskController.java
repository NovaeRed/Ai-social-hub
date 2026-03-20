package cn.redture.aiEngine.controller;

import cn.redture.aiEngine.pojo.dto.PolishRequest;
import cn.redture.aiEngine.pojo.dto.ScheduleRequest;
import cn.redture.aiEngine.pojo.dto.SmartReplyRequest;
import cn.redture.aiEngine.pojo.dto.SummarizeRequest;
import cn.redture.aiEngine.pojo.dto.TranslationRequest;
import cn.redture.aiEngine.pojo.vo.AiTaskDetailVO;
import cn.redture.aiEngine.pojo.vo.AiTaskItemVO;
import cn.redture.aiEngine.pojo.vo.ScheduleExtractionVO;
import cn.redture.aiEngine.pojo.vo.StreamOutputVO;
import cn.redture.aiEngine.service.AiInteractionService;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.integration.ai.dto.AiExternalMessageItem;
import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.pojo.vo.CursorPageResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI任务控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/tasks")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiInteractionService aiInteractionService;
    private final AiTaskService aiTaskService;
    private final AiExternalService aiExternalService;

    /**
     * 文本润色（流式）
     */
    @PostMapping(value = "/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamOutputVO> polish(@RequestBody PolishRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return aiInteractionService.polishStream(userId, request);
    }

    /**
     * 日程提取（同步）
     */
    @PostMapping("/schedule")
    public RestResult<ScheduleExtractionVO> extractSchedule(@RequestBody ScheduleRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return RestResult.success(aiInteractionService.extractSchedule(userId, request));
    }

    /**
     * 翻译（流式）
     */
    @PostMapping(value = "/translate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamOutputVO> translate(@RequestBody TranslationRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return aiInteractionService.translateStream(userId, request);
    }

    /**
     * 智能回复（流式）
     */
    @PostMapping(value = "/smart-reply", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamOutputVO> smartReply(@RequestBody SmartReplyRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();

        if ((request.getConversationHistory() == null || request.getConversationHistory().isEmpty())
                && request.getConversationPublicId() != null
                && !request.getConversationPublicId().isBlank()) {
            List<AiExternalMessageItem> contextMessages = aiExternalService.getRecentContextMessages(request.getConversationPublicId(), 10);
            request.setConversationHistory(toHistoryMessages(contextMessages));
        }

        return aiInteractionService.smartReplyStream(userId, request);
    }

    /**
     * 内容总结（流式）
     */
    @PostMapping(value = "/summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamOutputVO> summarize(@RequestBody SummarizeRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();

        if (request.getContent() == null || request.getContent().isBlank()) {
            List<AiExternalMessageItem> sourceMessages;

            if (request.getSelectedMessageIds() != null && !request.getSelectedMessageIds().isEmpty()) {
                sourceMessages = aiExternalService.getMessagesByIds(request.getSelectedMessageIds());
            } else if (request.getConversationPublicId() != null && !request.getConversationPublicId().isBlank()) {
                sourceMessages = aiExternalService.getRecentContextMessages(request.getConversationPublicId(), 50);
            } else {
                throw new InvalidInputException("缺少可总结内容: content 或 conversation_public_id 或 selected_message_ids 至少提供一个");
            }

            request.setContent(formatMessagesToText(sourceMessages));
        }

        return aiInteractionService.summarizeStream(userId, request);
    }

    /**
     * 获取可供用户显式选择的消息样本（用于 Persona Analysis / Custom Summary）
     */
    @GetMapping("/message-candidates")
    public RestResult<Map<String, List<AiExternalMessageItem>>> getMessageCandidates(
            @RequestParam(value = "conversation_public_id", required = false) String conversationPublicId,
            @RequestParam(value = "limit", defaultValue = "100") Integer limit) {
        Long userId = SecurityContextHolderUtil.getUserId();
        List<AiExternalMessageItem> items;

        if (conversationPublicId != null && !conversationPublicId.isBlank()) {
            items = aiExternalService.getUserMessagesInConversation(conversationPublicId, userId, limit);
        } else {
            items = aiExternalService.getUserRecentMessagesForAnalysis(userId, limit);
        }

        return RestResult.success(Map.of("items", items));
    }

    /**
     * 获取AI任务列表
     */
    @GetMapping
    public RestResult<CursorPageResult<AiTaskItemVO>> getTasks(
            @RequestParam(value = "task_type", required = false) String taskTypeStr,
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit) {

        Long userId = SecurityContextHolderUtil.getUserId();
        AiTaskType taskType = null;
        if (taskTypeStr != null && !taskTypeStr.isEmpty()) {
            try {
                taskType = AiTaskType.valueOf(taskTypeStr);
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("无效的任务类型: " + taskTypeStr);
            }
        }

        AiTaskStatus status = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                status = AiTaskStatus.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("无效的任务状态: " + statusStr);
            }
        }

        return RestResult.success(aiTaskService.getUserTasks(userId, taskType, status, cursor, limit));
    }

    /**
     * 获取AI任务详情
     */
    @GetMapping("/{publicId}")
    public RestResult<AiTaskDetailVO> getTaskDetail(@PathVariable String publicId) {
        AiTaskDetailVO detail = aiTaskService.getTaskDetail(publicId);
        return RestResult.success(detail);
    }

    private List<SmartReplyRequest.HistoryMessage> toHistoryMessages(List<AiExternalMessageItem> messages) {
        return messages.stream().map(m -> {
            SmartReplyRequest.HistoryMessage h = new SmartReplyRequest.HistoryMessage();
            h.setSender(m.getSender());
            h.setContent(m.getContent());
            h.setTimestamp(parseTimestamp(m.getTimestamp()));
            return h;
        }).toList();
    }

    private String formatMessagesToText(List<AiExternalMessageItem> messages) {
        StringBuilder sb = new StringBuilder();
        for (AiExternalMessageItem msg : messages) {
            String sender = msg.getSender() == null || msg.getSender().isBlank() ? "未知用户" : msg.getSender();
            String content = msg.getContent() == null ? "" : msg.getContent();
            sb.append(sender).append(": ").append(content).append("\n");
        }
        return sb.toString();
    }

    private OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp);
        } catch (Exception ignore) {
            return null;
        }
    }
}
