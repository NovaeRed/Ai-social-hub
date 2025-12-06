package cn.redture.common.exception.businessException;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AccessDeniedException extends BaseException {

    public AccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
