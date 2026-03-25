package cn.redture.common.exception.businessException;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 熔断触发异常（503）
 */
public class CircuitBreakerException extends BaseException {

    public CircuitBreakerException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, ErrorCodes.CIRCUIT_BREAKER_OPEN);
    }
}
