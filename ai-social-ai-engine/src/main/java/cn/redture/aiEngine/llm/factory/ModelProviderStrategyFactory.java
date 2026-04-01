package cn.redture.aiEngine.llm.factory;

import cn.redture.aiEngine.llm.strategy.ModelProvider;
import cn.redture.aiEngine.llm.strategy.ModelProviderStrategy;
import cn.redture.aiEngine.llm.util.ModelProviderUtil;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型策略工厂
 *
 * <p>通过注解自动注册 provider -> strategy，并提供静态工厂方法</p>
 */
@Slf4j
@Component
public class ModelProviderStrategyFactory {

    private final Map<String, ModelProviderStrategy> strategyMap;
    private static volatile Map<String, ModelProviderStrategy> staticStrategyMap = Map.of();

    public ModelProviderStrategyFactory(List<ModelProviderStrategy> strategies) {
        Map<String, ModelProviderStrategy> mutableMap = new HashMap<>();
        for (ModelProviderStrategy strategy : strategies) {
            Class<?> targetClass = AopUtils.getTargetClass(strategy);
            ModelProvider annotation = targetClass.getAnnotation(ModelProvider.class);
            String provider = annotation != null ? annotation.value() : strategy.providerCode();
            String normalizedProvider = ModelProviderUtil.normalizeProvider(provider);
            mutableMap.put(normalizedProvider, strategy);
        }
        this.strategyMap = Map.copyOf(mutableMap);
    }

    @PostConstruct
    public void initStaticFactory() {
        staticStrategyMap = strategyMap;
        log.info("LLM 策略工厂初始化完成，可用 provider: {}", strategyMap.keySet());
    }

    /**
     * 实例方式获取策略。
     */
    public ModelProviderStrategy getProviderStrategy(String provider) {
        String normalized = ModelProviderUtil.normalizeProvider(provider);
        ModelProviderStrategy strategy = strategyMap.get(normalized);
        if (strategy == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "当前服务暂不支持该模型提供商: " + provider, ErrorCodes.MODEL_OPTION_INVALID);
        }
        return strategy;
    }

    /**
     * 静态工厂方法。
     */
    public static ModelProviderStrategy getStrategy(String provider) {
        String normalized = ModelProviderUtil.normalizeProvider(provider);
        ModelProviderStrategy strategy = staticStrategyMap.get(normalized);
        if (strategy == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST,
                    "当前服务暂不支持该模型提供商: " + provider,
                    ErrorCodes.MODEL_OPTION_INVALID);
        }
        return strategy;
    }

}
