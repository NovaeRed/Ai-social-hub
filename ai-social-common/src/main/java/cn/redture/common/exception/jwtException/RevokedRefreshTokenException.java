package cn.redture.common.exception.jwtException;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 刷新令牌已被撤销（登出、改密码、封号等）。
 */
public class RevokedRefreshTokenException extends BaseException {

    public RevokedRefreshTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message, "REFRESH_TOKEN_REVOKED");
    }
}

