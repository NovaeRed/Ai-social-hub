package cn.redture.aiEngine.consumer;

import cn.redture.aiEngine.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.persona.handler.AiPersonaTaskHandlerRegistry;
import cn.redture.common.util.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static cn.redture.common.constants.RedisConstants.PERSONA_TASK_QUEUE_KEY;

/**
 * 简单的 AI 画像任务消费者，从 Redis List 中轮询任务并处理。
 * 初期仅做日志输出或占位处理，后续可扩展为真正调用大模型等操作。
 */
@Slf4j
@Component
public class AiPersonaTaskConsumer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ThreadPoolTaskExecutor aiPersonaTaskExecutor;

    @Resource
    private AiPersonaTaskHandlerRegistry handlerRegistry;

    private Thread consumerThread;

    @Value("${ai.persona.consumer.enabled:false}")
    private boolean consumerEnabled;

    @PostConstruct
    public void start() {
        if (!consumerEnabled) {
            log.debug("AI 画像任务消费者已禁用 (ai.persona.consumer.enabled=false)");
            return;
        }
        consumerThread = new Thread(this::consumeLoop, "ai-persona-task-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("AI 画像任务消费者线程已启动");
    }

    @PreDestroy
    public void stop() {
        if (consumerThread != null && consumerThread.isAlive()) {
            consumerThread.interrupt();
        }
    }

    private void consumeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 阻塞式弹出队列尾部元素（模拟简单队列）, 超时5秒
                String taskJson = stringRedisTemplate.opsForList().rightPop(PERSONA_TASK_QUEUE_KEY, Duration.ofSeconds(5));
                if (taskJson == null) {
                    continue;
                }

                AiPersonaTaskDTO task = JsonUtil.fromJson(taskJson, AiPersonaTaskDTO.class);
                aiPersonaTaskExecutor.submit(() -> handlerRegistry.dispatch(task));
            } catch (Exception e) {
                log.error("消费 AI 画像任务时发生异常", e);
            }
        }
    }

}
