package cn.redture.common.constants;

/**
 * 统一业务错误码常量。
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    public static final String INVALID_INPUT = "INVALID_INPUT";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String CIRCUIT_BREAKER_OPEN = "CIRCUIT_BREAKER_OPEN";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String FRIENDSHIP_BUSINESS = "FRIENDSHIP_BUSINESS";
    public static final String RESOURCE_TYPE_CONVERT = "RESOURCE_TYPE_CONVERT";

    public static final String TOKEN_INVALID = "TOKEN_INVALID";
    public static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String TOKEN_BLACKLISTED = "TOKEN_BLACKLISTED";
    public static final String REFRESH_TOKEN_INVALID = "REFRESH_TOKEN_INVALID";
    public static final String REFRESH_TOKEN_EXPIRED = "REFRESH_TOKEN_EXPIRED";
    public static final String REFRESH_TOKEN_REVOKED = "REFRESH_TOKEN_REVOKED";

    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String AI_TASK_DLQ = "AI_TASK_DLQ";

    public static final String MODEL_OPTION_INVALID = "MODEL_OPTION_INVALID";
    public static final String MODEL_NOT_ENABLED = "MODEL_NOT_ENABLED";
    public static final String MODEL_CAPABILITY_MISMATCH = "MODEL_CAPABILITY_MISMATCH";
    public static final String PROMPT_INJECTION_BLOCKED = "PROMPT_INJECTION_BLOCKED";
    public static final String MODEL_OUTPUT_SECURITY_BLOCKED = "MODEL_OUTPUT_SECURITY_BLOCKED";
}