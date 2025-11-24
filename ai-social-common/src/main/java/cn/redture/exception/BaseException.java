package cn.redture.exception;

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
     * 使用HTTP状态和消息构造异常
     * @param status HTTP状态
     * @param message 异常信息
     */
    public BaseException(HttpStatus status, String message) {
        super(message);
        this.code = status.value();
        this.message = message;
    }

    /**
     * 使用自定义状态码和消息构造异常
     * @param code 自定义状态码
     * @param message 异常信息
     */
    public BaseException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
