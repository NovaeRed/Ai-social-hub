package cn.redture.aiEngine.pojo.enums;

import lombok.Getter;

/**
 * AI服务提供商枚举
 */
@Getter
public enum AiProvider {
    /**
     * 阿里云通义千问
     */
    QWEN("qwen", "阿里云通义千问"),

    /**
     * 本地模型
     */
    LOCAL("local", "本地模型");

    private final String code;
    private final String description;

    AiProvider(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AiProvider fromCode(String code) {
        for (AiProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown AI provider: " + code);
    }
}
