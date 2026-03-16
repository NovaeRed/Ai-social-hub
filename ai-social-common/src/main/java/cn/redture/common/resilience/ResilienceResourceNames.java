package cn.redture.common.resilience;

/**
 * Sentinel 资源名定义，避免业务代码散落硬编码。
 */
public final class ResilienceResourceNames {

    private ResilienceResourceNames() {
    }

    public static final String CHAT_CREATE_MESSAGE = "chat:message:create";
}
