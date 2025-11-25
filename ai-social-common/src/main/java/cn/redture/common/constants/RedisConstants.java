package cn.redture.common.constants;

public class RedisConstants {

    /**
     * 刷新令牌 Redis Key 前缀：auth:refresh:<userId>
     */
    public static final String AUTH_REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    /**
     * JWT 黑名单 Redis Key 前缀：blacklist:jwt:<jti>
     */
    public static final String AUTH_JWT_BLACKLIST_KEY_PREFIX = "blacklist:jwt:";
}
