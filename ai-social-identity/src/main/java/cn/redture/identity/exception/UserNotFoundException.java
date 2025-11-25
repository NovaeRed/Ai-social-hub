package cn.redture.identity.exception;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 用户未找到异常
 */
public class UserNotFoundException extends BaseException {

    private static final String ERROR_CODE = "USER_NOT_FOUND";

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "指定的用户不存在", ERROR_CODE);
    }

    public UserNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message, ERROR_CODE);
    }
}

