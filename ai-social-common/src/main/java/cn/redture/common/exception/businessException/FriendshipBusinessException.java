package cn.redture.common.exception.businessException;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 好友/好友请求相关的业务异常。
 */
public class FriendshipBusinessException extends BaseException {

    public FriendshipBusinessException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
