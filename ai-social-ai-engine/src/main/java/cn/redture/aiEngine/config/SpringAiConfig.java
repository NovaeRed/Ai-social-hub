package cn.redture.aiEngine.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置
 */
@Configuration
public class SpringAiConfig {

    @Value("${ai.qwen.default-model}")
    public String QWEN_MODEL;

    @Value("${ai.qwen.api-key}")
    public String QWEN_API_KEY;

    /**
     * 通义千问 ChatModel
     */
    @Bean(name = "qwen")
    public ChatModel qwen() {
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                        .apiKey(QWEN_API_KEY)
                        .build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(QWEN_MODEL)
                        .build())
                .build();
    }

    /**
     * 通义千问 ChatClient
     */
    @Bean(name = "qwenClient")
    public ChatClient qwenClient(@Qualifier("qwen") ChatModel qwen) {
        return ChatClient.builder(qwen).build();
    }

}
