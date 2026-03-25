package cn.redture.aiEngine.model.strategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记模型供应商策略的 provider 编码
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModelProvider {

    /**
     * 供应商编码，例如 dashscope
     */
    String value();
}
