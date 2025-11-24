package cn.redture.common.exception.jwt;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 刷新令牌已过期或失效，需要重新登录。
 */
public class ExpiredRefreshTokenException extends BaseException {

    public ExpiredRefreshTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message, "REFRESH_TOKEN_EXPIRED");
    }
}

