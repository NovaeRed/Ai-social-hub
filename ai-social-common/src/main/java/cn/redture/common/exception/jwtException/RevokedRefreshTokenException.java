package cn.redture.common.exception.jwtException;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 刷新令牌已被撤销（登出、改密码、封号等）。
 */
public class RevokedRefreshTokenException extends BaseException {

    public RevokedRefreshTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message, ErrorCodes.REFRESH_TOKEN_REVOKED);
    }
}

