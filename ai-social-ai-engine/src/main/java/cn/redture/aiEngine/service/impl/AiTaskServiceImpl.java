package cn.redture.aiEngine.service.impl;

import cn.redture.aiEngine.pojo.vo.AiTaskDetailVO;
import cn.redture.aiEngine.pojo.vo.AiTaskItemVO;
import cn.redture.common.pojo.vo.CursorPageResult;
import cn.redture.aiEngine.pojo.entity.AiTask;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.mapper.AiTaskMapper;
import cn.redture.aiEngine.service.AiTaskService;
import cn.redture.common.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskServiceImpl implements AiTaskService {

    private final AiTaskMapper aiTaskMapper;

    @Override
    public AiTask createTask(Long userId, AiTaskType taskType, Map<String, Object> inputData) {
        AiTask task = new AiTask();
        task.setPublicId(IdUtil.nextId());
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setTaskStatus(AiTaskStatus.PENDING);
        task.setInputPayload(inputData);
        task.setCreatedAt(OffsetDateTime.now());

        aiTaskMapper.insert(task);
        log.info("Created AI task: id={}, publicId={}, type={}", task.getId(), task.getPublicId(), taskType);

        return task;
    }

    @Override
    public void updateTaskStatus(Long taskId, AiTaskStatus status) {
        AiTask task = new AiTask();
        task.setId(taskId);
        task.setTaskStatus(status);

        if (status == AiTaskStatus.PROCESSING) {
            task.setStartedAt(OffsetDateTime.now());
        } else if (status == AiTaskStatus.COMPLETED || status == AiTaskStatus.FAILED) {
            task.setCompletedAt(OffsetDateTime.now());
        }

        aiTaskMapper.updateById(task);
        log.info("Updated task status: id={}, status={}", taskId, status);
    }

    @Override
    public void updateTaskResult(Long taskId, AiTaskStatus status, Map<String, Object> result, String errorMessage) {
        AiTask task = new AiTask();
        task.setId(taskId);
        task.setTaskStatus(status);
        task.setOutputPayload(result);
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(OffsetDateTime.now());

        aiTaskMapper.updateById(task);
        log.info("Updated task result: id={}, status={}", taskId, status);
    }

    @Override
    public AiTask getTaskByPublicId(String publicId) {
        return aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>().eq(AiTask::getPublicId, publicId));
    }

    @Override
    public CursorPageResult<AiTaskItemVO> getUserTasks(Long userId, AiTaskType taskType, AiTaskStatus status, Long cursor, Integer limit) {
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getUserId, userId)
                .orderByDesc(AiTask::getId);

        if (taskType != null) {
            wrapper.eq(AiTask::getTaskType, taskType);
        }

        if (status != null) {
            wrapper.eq(AiTask::getTaskStatus, status);
        }

        if (cursor != null) {
            wrapper.lt(AiTask::getId, cursor);
        }

        wrapper.last("LIMIT " + (limit + 1));

        List<AiTask> tasks = aiTaskMapper.selectList(wrapper);

        boolean hasMore = tasks.size() > limit;
        List<AiTask> page = hasMore ? tasks.subList(0, limit) : tasks;

        List<AiTaskItemVO> items = page.stream().map(this::convertToVO).collect(Collectors.toList());

        CursorPageResult<AiTaskItemVO> result = new CursorPageResult<>();
        result.setItems(items);
        result.setHasMore(hasMore);

        if (!page.isEmpty()) {
            result.setNextCursor(page.getLast().getId());
        }

        return result;
    }

    @Override
    public AiTaskDetailVO getTaskDetail(String publicId) {
        AiTask task = getTaskByPublicId(publicId);
        if (task == null) {
            return null;
        }

        AiTaskDetailVO vo = new AiTaskDetailVO();
        vo.setPublicId(task.getPublicId());
        vo.setType(task.getTaskType().name());
        vo.setStatus(task.getTaskStatus().name());
        vo.setInputPayload(task.getInputPayload());
        vo.setOutputPayload(task.getOutputPayload());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setModelConfig(task.getModelConfig());
        vo.setTokenUsage(task.getTokenUsage());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setCompletedAt(task.getCompletedAt());
        return vo;
    }

    private AiTaskItemVO convertToVO(AiTask task) {
        AiTaskItemVO vo = new AiTaskItemVO();
        vo.setPublicId(task.getPublicId());
        vo.setType(task.getTaskType());
        vo.setStatus(task.getTaskStatus());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setResult(task.getOutputPayload());
        vo.setModelConfig(task.getModelConfig());
        return vo;
    }
}
