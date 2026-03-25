package cn.redture.common.exception.businessException;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 限流触发异常（429）
 */
public class RateLimitException extends BaseException {

    public RateLimitException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message, ErrorCodes.RATE_LIMITED);
    }
}
