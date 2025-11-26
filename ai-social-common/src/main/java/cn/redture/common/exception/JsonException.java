package cn.redture.common.exception;

import org.springframework.http.HttpStatus;

/**
 * JSON 处理相关的自定义异常
 */
public class JsonException extends BaseException {

    public JsonException(HttpStatus status, String message) {
        super(status, message, null);
    }

    public JsonException(int code, String message) {
        super(code, message, null);
    }

    public JsonException(HttpStatus status, String message, String errorCode) {
        super(status, message, errorCode);
    }

    public JsonException(int code, String message, String errorCode) {
        super(code, message, errorCode);
    }
}

