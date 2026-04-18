package cn.redture.aiEngine.llm.factory;

import cn.redture.aiEngine.llm.strategy.ModelProvider;
import cn.redture.aiEngine.llm.strategy.ModelProviderStrategy;
import cn.redture.aiEngine.llm.util.ModelProviderUtil;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 模型策略工厂
 *
 * <p>通过注解自动注册 provider -> strategy。</p>
 */
@Slf4j
@Component
public class ModelProviderStrategyFactory {

    private final Map<String, ModelProviderStrategy> strategyMap;

    /**
     * 构造并初始化 provider -> strategy 映射。
     *
     * @param strategies Spring 注入的所有策略实现
     */
    public ModelProviderStrategyFactory(List<ModelProviderStrategy> strategies) {
        Map<String, ModelProviderStrategy> mutableMap = new HashMap<>();
        Map<String, String> mutableProviderImplMap = new HashMap<>();
        for (ModelProviderStrategy strategy : strategies) {
            Class<?> targetClass = AopUtils.getTargetClass(strategy);
            ModelProvider annotation = targetClass.getAnnotation(ModelProvider.class);
            String provider = annotation != null ? annotation.value() : strategy.providerCode();
            String normalizedProvider = ModelProviderUtil.normalizeProvider(provider);
            String implClassName = targetClass.getName();

            if (normalizedProvider.isBlank()) {
                throw new IllegalStateException("LLM provider 不能为空，策略实现=" + implClassName
                        + "，请检查 @ModelProvider 或 providerCode() 返回值");
            }

            String previousImpl = mutableProviderImplMap.put(normalizedProvider, implClassName);
            if (previousImpl != null && !previousImpl.equals(implClassName)) {
                String conflictMessage = "LLM provider 注册冲突: provider=" + normalizedProvider
                        + ", 已存在实现=" + previousImpl
                        + ", 冲突实现=" + implClassName
                        + "。请确保每个 provider 仅绑定一个策略实现";
                log.error(conflictMessage);
                throw new IllegalStateException(conflictMessage);
            }

            mutableMap.put(normalizedProvider, strategy);
        }
        this.strategyMap = Map.copyOf(mutableMap);
        Map<String, String> sortedMappings = new TreeMap<>(mutableProviderImplMap);
        log.info("LLM 策略工厂初始化完成，可用 provider: {}", sortedMappings.keySet());
        log.info("LLM provider -> strategy 实现类映射: {}", sortedMappings);
    }

    /**
     * 实例方式获取策略。
     *
     * @param provider 厂商编码
     * @return 对应厂商策略
     */
    public ModelProviderStrategy getProviderStrategy(String provider) {
        String normalized = ModelProviderUtil.normalizeProvider(provider);
        ModelProviderStrategy strategy = strategyMap.get(normalized);
        if (strategy == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "当前服务暂不支持该模型提供商: " + provider, ErrorCodes.MODEL_OPTION_INVALID);
        }
        return strategy;
    }

}
