package cn.redture.aiEngine.model.strategy.impl;

import cn.redture.aiEngine.model.provider.dashscope.DashscopeModelConfigResolver;
import cn.redture.aiEngine.model.provider.dashscope.DashscopeOkHttpClient;
import cn.redture.aiEngine.model.strategy.ModelProvider;
import cn.redture.aiEngine.model.strategy.ModelProviderStrategy;
import cn.redture.aiEngine.model.util.ModelProviderUtil;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * DashScope 供应商策略实现
 */
@Slf4j
@Component
@ModelProvider("dashscope")
@RequiredArgsConstructor
public class DashscopeModelProviderStrategy implements ModelProviderStrategy {

    private final DashscopeModelConfigResolver configResolver;
    private final DashscopeOkHttpClient dashscopeOkHttpClient;

    @Override
    public String providerCode() {
        return "dashscope";
    }

    @Override
    public Flux<String> stream(String prompt, String modelName) {
        DashscopeModelConfigResolver.ResolvedModelConfig config = resolveConfig(modelName);
        return dashscopeOkHttpClient.stream(config.chatCompletionsUrl(), config.apiKey(), config.modelName(), prompt);
    }

    @Override
    public String call(String prompt, String modelName) {
        DashscopeModelConfigResolver.ResolvedModelConfig config = resolveConfig(modelName);
        return dashscopeOkHttpClient.call(config.chatCompletionsUrl(), config.apiKey(), config.modelName(), prompt);
    }

    @Override
    public String callWithTools(String prompt, String modelName) {
        // DashScope HTTP 客户端阶段先复用普通文本调用，后续再扩展 tools 协议映射。
        return call(prompt, modelName);
    }

    private DashscopeModelConfigResolver.ResolvedModelConfig resolveConfig(String modelName) {
        String resolvedModel = ModelProviderUtil.normalizeModelName(modelName);
        if (resolvedModel.isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "模型名称不能为空，请检查模型路由配置", ErrorCodes.MODEL_OPTION_INVALID);
        }

        DashscopeModelConfigResolver.ResolvedModelConfig config = configResolver.resolveByModelName(providerCode(), resolvedModel);
        if (config.apiKey().isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "模型 " + resolvedModel + " 缺少 DashScope apiKey 配置", ErrorCodes.MODEL_OPTION_INVALID);
        }
        if (config.chatCompletionsUrl().isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "模型 " + resolvedModel + " 缺少 DashScope chat endpoint 配置", ErrorCodes.MODEL_OPTION_INVALID);
        }
        return config;
    }
}
