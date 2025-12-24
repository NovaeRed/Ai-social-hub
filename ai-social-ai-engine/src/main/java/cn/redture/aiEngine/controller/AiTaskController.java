package cn.redture.aiEngine.controller;

import cn.redture.aiEngine.pojo.dto.*;
import cn.redture.aiEngine.pojo.vo.*;
import cn.redture.aiEngine.service.AiInteractionService;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.exception.businessException.InvalidInputException;
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
        return aiInteractionService.smartReplyStream(userId, request);
    }

    /**
     * 内容总结（流式）
     */
    @PostMapping(value = "/summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamOutputVO> summarize(@RequestBody SummarizeRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return aiInteractionService.summarizeStream(userId, request);
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

    /**
     * 性格分析（异步）
     */
    @PostMapping("/personality-analysis")
    public RestResult<PersonaAnalysisVO> analyzePersona(@RequestBody PersonaAnalysisRequest request) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return RestResult.accepted(aiInteractionService.analyzePersonaAsync(userId, request));
    }
}
