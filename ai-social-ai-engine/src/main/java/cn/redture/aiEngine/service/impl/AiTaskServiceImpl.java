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

    /**
     * 创建一条 AI 任务记录并初始化为待处理状态。
     *
     * @param userId 调用方用户 ID
     * @param taskType 任务类型
     * @param inputData 任务输入参数
     * @return 持久化后的任务实体
     */
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

    /**
     * 更新任务状态，并根据状态自动记录开始或结束时间。
     *
     * @param taskId 任务主键 ID
     * @param status 新状态
     */
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

    /**
     * 更新任务执行结果与错误信息。
     *
     * @param taskId 任务主键 ID
     * @param status 新状态
     * @param result 输出结果
     * @param errorMessage 错误信息
     */
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

    /**
     * 通过任务公开 ID 查询任务。
     *
     * @param publicId 任务公开 ID
     * @return 任务实体，不存在时返回 null
     */
    @Override
    public AiTask getTaskByPublicId(String publicId) {
        return aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>().eq(AiTask::getPublicId, publicId));
    }

    /**
     * 分页查询用户任务列表（游标模式）。
     *
     * @param userId 用户 ID
     * @param taskType 任务类型过滤条件
     * @param status 任务状态过滤条件
     * @param cursor 游标（上一页最后一条任务 ID）
     * @param limit 分页大小
     * @return 游标分页结果
     */
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

    /**
     * 查询任务详情并转换为前端展示对象。
     *
     * @param publicId 任务公开 ID
     * @return 任务详情，不存在时返回 null
     */
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
        vo.setRequestedModelOptionCode(readModelOptionCode(task.getInputPayload()));
        vo.setResolvedModelName(task.getModelConfig() == null ? null : task.getModelConfig().getModelName());
        vo.setResolvedProvider(task.getProvider());
        vo.setInputPayload(task.getInputPayload());
        vo.setOutputPayload(task.getOutputPayload());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setModelConfig(task.getModelConfig());
        vo.setTokenUsage(task.getTokenUsage());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setCompletedAt(task.getCompletedAt());
        return vo;
    }

    /**
     * 将任务实体转换为列表项视图对象。
     *
     * @param task 任务实体
     * @return 列表项视图对象
     */
    private AiTaskItemVO convertToVO(AiTask task) {
        AiTaskItemVO vo = new AiTaskItemVO();
        vo.setPublicId(task.getPublicId());
        vo.setType(task.getTaskType());
        vo.setStatus(task.getTaskStatus());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setRequestedModelOptionCode(readModelOptionCode(task.getInputPayload()));
        vo.setResolvedModelName(task.getModelConfig() == null ? null : task.getModelConfig().getModelName());
        vo.setResolvedProvider(task.getProvider());
        vo.setResult(task.getOutputPayload());
        vo.setModelConfig(task.getModelConfig());
        return vo;
    }

    private String readModelOptionCode(Map<String, Object> inputPayload) {
        if (inputPayload == null) {
            return null;
        }
        Object value = inputPayload.get("model_option_code");
        if (!(value instanceof String optionCode) || optionCode.isBlank()) {
            return null;
        }
        return optionCode.trim();
    }
}
