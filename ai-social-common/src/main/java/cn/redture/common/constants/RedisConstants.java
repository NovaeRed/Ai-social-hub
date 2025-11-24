package cn.redture.common.constants;

public class RedisConstants {

    /**
     * 刷新令牌 Redis Key 前缀：auth:refresh:<userId>
     */
    public static final String AUTH_REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    /**
     * 刷新令牌有效期（秒）：7 天
     */
    public static final long AUTH_REFRESH_TOKEN_TTL_SECONDS = 7L * 24 * 60 * 60;
}
