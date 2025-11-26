package cn.redture.aiEngine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 画像任务执行线程池配置。
 */
@Configuration
public class AiPersonaExecutorConfig {

    @Value("${ai.persona.executor.threadNamePrefix:ai-persona-}")
    private String threadNamePrefix;

    @Value("${ai.persona.executor.corePoolSize:4}")
    private int corePoolSize;

    @Value("${ai.persona.executor.maxPoolSize:8}")
    private int maxPoolSize;

    @Value("${ai.persona.executor.queueCapacity:100}")
    private int queueCapacity;

    @Bean
    public ThreadPoolTaskExecutor aiPersonaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
