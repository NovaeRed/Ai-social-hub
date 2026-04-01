package cn.redture.aiEngine.llm.provider.dashscope;

import cn.redture.aiEngine.llm.config.AiChatProperties;
import cn.redture.aiEngine.llm.config.AiProviderProperties;
import cn.redture.aiEngine.llm.util.ModelProviderUtil;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DashScope 模型配置解析器
 * <p>
 * 基于 ai.providers / ai.chat 配置解析调用参数。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class DashscopeModelConfigResolver {

    private final AiProviderProperties providerProperties;
    private final AiChatProperties chatProperties;

    /**
     * 根据 provider 和 modelName 解析完整的模型配置。
     * <p>
     * 配置查询优先级：
     * 1. 厂商级 apiKey (ai.providers.<provider>.api-key)
     * 2. 厂商级 URL + chat endpoint (ai.providers.<provider>.url + endpoints.chat)
     * </p>
     */
    public ResolvedModelConfig resolveByModelName(String provider, String modelName) {
        String normalizedProvider = ModelProviderUtil.normalizeProvider(provider);
        String normalizedModelName = ModelProviderUtil.normalizeModelName(modelName);

        AiProviderProperties.ProviderConfig providerConfig = getProviderConfig(normalizedProvider);
        if (providerConfig == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "未找到 provider 配置: provider=" + provider, ErrorCodes.MODEL_OPTION_INVALID);
        }

        AiChatProperties.ModelCandidateConfig candidate = findCandidateByProviderAndModel(normalizedProvider, normalizedModelName);
        if (candidate == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "未找到候选模型配置: provider=" + provider + ", modelName=" + modelName, ErrorCodes.MODEL_OPTION_INVALID);
        }

        String apiKey = safe(providerConfig.getApiKey());
        String chatCompletionsUrl = buildChatCompletionsUrl(providerConfig);

        if (apiKey.isBlank() || chatCompletionsUrl.isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "未找到完整的 LLM 模型配置: provider=" + provider + ", modelName=" + modelName, ErrorCodes.MODEL_OPTION_INVALID);
        }

        return new ResolvedModelConfig(normalizedProvider, normalizedModelName, apiKey, chatCompletionsUrl);
    }

    /**
     * 从 ai.providers 中获取厂商配置
     */
    private AiProviderProperties.ProviderConfig getProviderConfig(String normalizedProvider) {
        if (providerProperties.getProviders() == null || providerProperties.getProviders().isEmpty()) {
            return null;
        }

        AiProviderProperties.ProviderConfig config = providerProperties.getProviders().get(normalizedProvider);
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            return config;
        }

        for (var entry : providerProperties.getProviders().entrySet()) {
            if (normalizedProvider.equals(ModelProviderUtil.normalizeProvider(entry.getKey()))) {
                if (entry.getValue() != null && Boolean.TRUE.equals(entry.getValue().getEnabled())) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * 从 chat.candidates 中查找与 provider/modelName 匹配的候选模型
     */
    private AiChatProperties.ModelCandidateConfig findCandidateByProviderAndModel(
            String provider, String modelName) {
        if (chatProperties.getCandidates() == null) {
            return null;
        }

        for (AiChatProperties.ModelCandidateConfig candidate : chatProperties.getCandidates()) {
            if (candidate == null || !Boolean.TRUE.equals(candidate.getEnabled())) {
                continue;
            }
            if (provider.equals(ModelProviderUtil.normalizeProvider(candidate.getProvider()))
                    && modelName.equals(ModelProviderUtil.normalizeModelName(candidate.getModel()))) {
                return candidate;
            }
        }

        return null;
    }

    private String buildChatCompletionsUrl(AiProviderProperties.ProviderConfig providerConfig) {
        String baseUrl = safe(providerConfig.getUrl());
        if (baseUrl.isBlank()) {
            return "";
        }

        Map<String, String> endpoints = providerConfig.getEndpoints();
        String chatPath = endpoints == null ? "" : safe(endpoints.get("chat"));
        if (chatPath.isBlank()) {
            return "";
        }

        if (!baseUrl.endsWith("/") && !chatPath.startsWith("/")) {
            return baseUrl + "/" + chatPath;
        }
        if (baseUrl.endsWith("/") && chatPath.startsWith("/")) {
            return baseUrl + chatPath.substring(1);
        }
        return baseUrl + chatPath;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record ResolvedModelConfig(String provider, String modelName, String apiKey, String chatCompletionsUrl) {
    }
}
