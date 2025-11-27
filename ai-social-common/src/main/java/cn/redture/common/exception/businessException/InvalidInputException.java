package cn.redture.common.exception.businessException;

import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 输入参数无效异常
 * <p>
 * 用于校验请求参数格式不正确等场景。
 */
public class InvalidInputException extends BaseException {

    private static final String DEFAULT_ERROR_CODE = "INVALID_INPUT";

    /**
     * 构造一个“输入参数无效”异常
     *
     * @param message 具体的错误信息，例如 "邮箱格式不正确"
     */
    public InvalidInputException(String message) {
        super(HttpStatus.BAD_REQUEST, message, DEFAULT_ERROR_CODE);
    }
}
