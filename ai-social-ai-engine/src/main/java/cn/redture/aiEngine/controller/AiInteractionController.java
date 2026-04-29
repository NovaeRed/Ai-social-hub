package cn.redture.aiEngine.controller;

import cn.redture.aiEngine.pojo.dto.PolishRequest;
import cn.redture.aiEngine.pojo.dto.ScheduleRequest;
import cn.redture.aiEngine.pojo.dto.SmartReplyRequest;
import cn.redture.aiEngine.pojo.dto.SummarizeRequest;
import cn.redture.aiEngine.pojo.dto.TranslationRequest;
import cn.redture.aiEngine.pojo.vo.ScheduleExtractionVO;
import cn.redture.aiEngine.pojo.vo.StreamOutputVO;
import cn.redture.aiEngine.service.AiOnlineInteractionService;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.integration.ai.dto.AiExternalMessageItem;
import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 在线交互控制器。
 */
@Slf4j
@RestController
@RequestMapping("/ai/interactions")
@RequiredArgsConstructor
public class AiInteractionController {

    private final AiOnlineInteractionService aiOnlineInteractionService;
    private final AiExternalService aiExternalService;

    @PostMapping(value = "/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamOutputVO> polish(@RequestBody PolishRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return aiOnlineInteractionService.polishStream(userId, request);
    }

    @PostMapping("/schedule")
    public RestResult<ScheduleExtractionVO> extractSchedule(@RequestBody ScheduleRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return RestResult.success(aiOnlineInteractionService.extractSchedule(userId, request));
    }

    @PostMapping(value = "/translate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamOutputVO> translate(@RequestBody TranslationRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return aiOnlineInteractionService.translateStream(userId, request);
    }

    @PostMapping(value = "/smart-reply", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamOutputVO> smartReply(@RequestBody SmartReplyRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();

        if ((request.getConversationHistory() == null || request.getConversationHistory().isEmpty())
                && request.getConversationPublicId() != null
                && !request.getConversationPublicId().isBlank()) {
            List<AiExternalMessageItem> contextMessages = aiExternalService.getRecentContextMessages(request.getConversationPublicId(), 20);
            request.setConversationHistory(toHistoryMessages(contextMessages));
        }

        return aiOnlineInteractionService.smartReplyStream(userId, request);
    }

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

        return aiOnlineInteractionService.summarizeStream(userId, request);
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
            String ts = msg.getTimestamp() == null || msg.getTimestamp().isBlank() ? "" : ("[" + msg.getTimestamp() + "] ");
            sb.append(ts).append(sender).append(": ").append(content).append("\n");
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
