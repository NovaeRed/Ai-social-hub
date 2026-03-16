package cn.redture.common.exception.businessException;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 限流触发异常（429）
 */
public class RateLimitException extends BaseException {

    private static final String DEFAULT_ERROR_CODE = "RATE_LIMITED";

    public RateLimitException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message, DEFAULT_ERROR_CODE);
    }
}
