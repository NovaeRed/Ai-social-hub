package cn.redture.aiEngine.service;

import cn.redture.aiEngine.pojo.vo.AiTaskDetailVO;
import cn.redture.aiEngine.pojo.vo.AiTaskItemVO;
import cn.redture.common.pojo.vo.CursorPageResult;
import cn.redture.aiEngine.pojo.entity.AiTask;
import cn.redture.aiEngine.pojo.enums.AiTaskStatus;
import cn.redture.aiEngine.pojo.enums.AiTaskType;

import java.util.Map;

/**
 * AI任务服务接口
 */
public interface AiTaskService {
    
    /**
     * 创建任务
     */
    AiTask createTask(Long userId, AiTaskType taskType, Map<String, Object> inputData);

    /**
     * 创建任务并直接写入路由审计信息，同时标记为处理中。
     */
    AiTask createTaskAndMarkProcessing(Long userId, AiTaskType taskType, Map<String, Object> inputData, String provider, String modelName);
    
    /**
     * 更新任务状态
     */
    void updateTaskStatus(Long taskId, AiTaskStatus status);
    
    /**
     * 更新任务结果
     */
    void updateTaskResult(Long taskId, AiTaskStatus status, Map<String, Object> result, String errorMessage);

    /**
     * 更新任务路由审计信息。
     */
    void updateTaskRouting(Long taskId, Map<String, Object> inputPayload, String provider, String modelName);

    /**
     * 更新任务路由审计信息并标记任务为处理中。
     */
    void updateTaskRoutingAndMarkProcessing(Long taskId, Map<String, Object> inputPayload, String provider, String modelName);
    
    /**
     * 根据公开ID查询任务
     */
    AiTask getTaskByPublicId(String publicId);
    
    /**
     * 查询用户任务列表
     */
    CursorPageResult<AiTaskItemVO> getUserTasks(Long userId, AiTaskType taskType, AiTaskStatus status, Long cursor, Integer limit);

    /**
     * 获取任务详情
     */
    AiTaskDetailVO getTaskDetail(String publicId);
}
