package cn.redture.aiEngine.controller;

import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.pojo.vo.AiTaskDetailVO;
import cn.redture.aiEngine.pojo.vo.AiTaskItemVO;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.integration.ai.AiExternalService;
import cn.redture.common.integration.ai.dto.AiExternalMessageItem;
import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.pojo.vo.CursorPageResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 任务查询控制器。
 */
@Slf4j
@RestController
@RequestMapping("/ai/jobs")
@RequiredArgsConstructor
public class AiJobController {

    private final AiTaskService aiTaskService;
    private final AiExternalService aiExternalService;

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

    @GetMapping("/{publicId}")
    public RestResult<AiTaskDetailVO> getTaskDetail(@PathVariable String publicId) {
        AiTaskDetailVO detail = aiTaskService.getTaskDetail(publicId);
        return RestResult.success(detail);
    }
}
