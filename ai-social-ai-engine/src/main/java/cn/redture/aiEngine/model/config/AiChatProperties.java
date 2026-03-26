package cn.redture.aiEngine.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 聊天模型配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.chat")
public class AiChatProperties {

    /**
     * 默认模型的 candidate ID
     */
    private String defaultModel;

    /**
     * 深度思考模型的 candidate ID (可选)
     */
    private String deepThinkingModel;

    /**
     * 候选模型列表。
     */
    private List<ModelCandidateConfig> candidates = new ArrayList<>();

    @Data
    public static class ModelCandidateConfig {
        /**
         * 模型候选 ID (全局唯一)
         */
        private String id;

        /**
         * 关联的厂商名称
         */
        private String provider;

        /**
         * 厂商的模型标识 (如: qwen-plus-latest)
         */
        private String model;

        /**
         * 是否启用
         */
        private Boolean enabled = Boolean.TRUE;

        /**
         * 可选: 温度参数
         */
        private Double temperature;

        /**
         * 可选: 最大 token 数
         */
        private Integer maxTokens;
    }
}
