package cn.redture.common.exception.jwtException;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Token过期的异常
 */
public class ExpiredTokenException extends BaseException {
    public ExpiredTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message, ErrorCodes.TOKEN_EXPIRED);
    }

    public ExpiredTokenException(String message, String errorCode) {
        super(HttpStatus.UNAUTHORIZED, message, errorCode);
    }
}

