package cn.redture.common.exception.jwtException;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 令牌已加入黑名单异常
 * 当令牌（通常是 access_token）因为登出或刷新而被主动吊销后，
 * 如果客户端仍尝试使用该令牌，应抛出此异常。
 */
public class TokenBlacklistedException extends BaseException {

    public TokenBlacklistedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message, "TOKEN_BLACKLISTED");
    }

    public TokenBlacklistedException() {
        this("会话已失效，请重新登录");
    }
}
