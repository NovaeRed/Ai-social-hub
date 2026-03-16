package cn.redture.common.constants;

public class RedisConstants {

    /**
     * 刷新令牌 Redis Key：auth:refresh:<userId>
     */
    public static final String AUTH_REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    /**
     * JWT 黑名单 Redis Key：blacklist:jwt:<jti>
     */
    public static final String AUTH_JWT_BLACKLIST_KEY_PREFIX = "blacklist:jwt:";

    /**
     * AI 画像任务队列 Redis Key：ai:persona:tasks
     */
    public static final String PERSONA_TASK_QUEUE_KEY = "ai:persona:tasks";
}
