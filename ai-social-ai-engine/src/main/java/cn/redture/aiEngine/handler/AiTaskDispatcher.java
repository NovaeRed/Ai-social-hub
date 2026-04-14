package cn.redture.aiEngine.handler;

import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import cn.redture.common.exception.businessException.ResourceTypeConvertException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内部任务分派器 (Strategy Pattern)
 */
@Slf4j
@Component
public class AiTaskDispatcher {

    private final Map<AsyncTaskDomain, AiTaskHandler> handlerMap;

    public AiTaskDispatcher(List<AiTaskHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(AiTaskHandler::getDomain, h -> h, (h1, h2) -> {
                    log.warn("发现重复的任务处理器: {} 和 {}", h1.getClass(), h2.getClass());
                    return h1;
                }));
        log.info("初始化 AiTaskDispatcher, 共注册了 {} 个 handler", handlerMap.size());
    }

    public void dispatch(String domainStr, String eventType, Long userId, String taskJson, String recordId) {
        AsyncTaskDomain domain;
        try {
            domain = AsyncTaskDomain.valueOf(domainStr);
        } catch (IllegalArgumentException e) {
            log.error("无法识别的 AiTaskDomain 领域字符串: {}", domainStr);
            throw new ResourceTypeConvertException("非法任务参数：" + domainStr);
        }

        AiTaskHandler handler = handlerMap.get(domain);
        if (handler != null) {
            try {
                handler.executeTask(taskJson, userId, eventType, recordId);
            } catch (Exception e) {
                // 原有的 Exception 需要抛给上一层 Consumer -> ErrorHandler 处理重试
                if (e instanceof BaseException) {
                    throw new BaseException(((BaseException) e).getCode(), "处理任务时发生业务异常: " + e.getMessage(), ErrorCodes.RESOURCE_TYPE_CONVERT);
                }
            }
        } else {
            log.warn("没有找到匹配的方法处理器处理领域: {}", domain);
            throw new ResourceTypeConvertException("未找到匹配的任务处理器：" + domain);
        }
    }
}