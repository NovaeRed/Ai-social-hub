package cn.redture.common.exception.businessException;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 熔断触发异常（503）
 */
public class CircuitBreakerException extends BaseException {

    private static final String DEFAULT_ERROR_CODE = "CIRCUIT_BREAKER_OPEN";

    public CircuitBreakerException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, DEFAULT_ERROR_CODE);
    }
}
