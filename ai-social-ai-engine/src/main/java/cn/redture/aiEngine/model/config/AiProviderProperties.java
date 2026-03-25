package cn.redture.aiEngine.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 厂商配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProviderProperties {

    /**
     * 各 provider 配置。
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    @Data
    public static class ProviderConfig {
        /**
         * 厂商服务基础 URL。
         */
        private String url;

        /**
         * API 密钥。
         */
        private String apiKey;

        /**
         * API 端点映射 (endpoint-name -> path)。
         */
        private Map<String, String> endpoints = new HashMap<>();

        /**
         * 是否启用该厂商。
         */
        private Boolean enabled = Boolean.TRUE;
    }
}
