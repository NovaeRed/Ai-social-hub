package cn.redture.common.exception.jwt;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 无效Token的异常（包括签名错误、格式错误等）
 */
public class InvalidTokenException extends BaseException {
    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}

