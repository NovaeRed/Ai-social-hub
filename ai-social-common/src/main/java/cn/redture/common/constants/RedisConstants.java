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
     * 统一异步任务 Streams Key：ai:async:tasks:stream
     */
    public static final String AI_ASYNC_TASK_STREAM_KEY = "ai:async:tasks:stream";

    /**
     * 统一异步任务 Streams 消费组：ai-async-group
     */
    public static final String AI_ASYNC_TASK_STREAM_GROUP = "ai-async-group";

    /**
     * 统一异步任务死信 Streams Key：ai:async:tasks:dlq
     */
    public static final String AI_ASYNC_TASK_DLQ_STREAM_KEY = "ai:async:tasks:dlq";

    /**
     * 统一异步任务死信 Streams 消费组：ai-async-dlq-group
     */
    public static final String AI_ASYNC_TASK_DLQ_STREAM_GROUP = "ai-async-dlq-group";
}
