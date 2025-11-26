package cn.redture.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;

/**
 * 自定义基础异常类
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseException extends RuntimeException {

    private final int code;
    private final String message;
    /**
     * 业务错误码，例如 REFRESH_TOKEN_EXPIRED，非必填
     */
    private final String errorCode;

    /**
     * 无参构造，提供默认值，兼容某些反序列化或框架场景
     */
    public BaseException() {
        this(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", null);
    }

    /**
     * 使用HTTP状态和消息构造异常
     * @param status HTTP状态
     * @param message 异常信息
     */
    public BaseException(HttpStatus status, String message) {
        this(status.value(), message, null);
    }

    /**
     * 使用自定义状态码和消息构造异常
     * @param code 自定义状态码
     * @param message 异常信息
     */
    public BaseException(int code, String message) {
        this(code, message, null);
    }

    /**
     * 使用HTTP状态、消息和错误码构造异常
     * @param status HTTP状态
     * @param message 异常信息
     * @param errorCode 业务错误码
     */
    public BaseException(HttpStatus status, String message, String errorCode) {
        this(status.value(), message, errorCode);
    }

    /**
     * 使用自定义状态码、消息和错误码构造异常
     * @param code 自定义状态码
     * @param message 异常信息
     * @param errorCode 业务错误码
     */
    public BaseException(int code, String message, String errorCode) {
        super(message);
        this.code = code;
        this.message = message;
        this.errorCode = errorCode;
    }
}
