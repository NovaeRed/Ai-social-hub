package cn.redture.common.exception.jwt;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus;
import cn.redture.common.exception.BaseException;

/**
 * 刷新令牌无效（格式错误、签名错误等）。
 */
public class InvalidRefreshTokenException extends BaseException {
    public InvalidRefreshTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message, "REFRESH_TOKEN_INVALID");
    }
}



