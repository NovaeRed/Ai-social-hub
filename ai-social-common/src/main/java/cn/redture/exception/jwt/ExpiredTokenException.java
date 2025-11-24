package cn.redture.exception.jwt;

import cn.redture.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Token过期的异常
 */
public class ExpiredTokenException extends BaseException {
    public ExpiredTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}

