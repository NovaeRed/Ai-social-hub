package cn.redture.gateway.security;

/**
 * 与认证相关的常量定义。
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /**
     * HTTP Header 中携带 JWT 的头名称。
     */
    public static final String TOKEN_HEADER = "Authorization";

    /**
     * JWT 前缀，例如 "Bearer "。
     */
    public static final String TOKEN_PREFIX = "Bearer ";
}

