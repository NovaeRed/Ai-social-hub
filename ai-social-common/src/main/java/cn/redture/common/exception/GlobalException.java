package cn.redture.common.exception;

import cn.redture.common.pojo.model.RestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 捕获Controller层抛出的异常，并返回统一的JSON响应格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalException {

    /**
     * 处理自定义的业务异常
     * @param e BaseException
     * @return RestResult
     */
    @ExceptionHandler(BaseException.class)
    public RestResult<Void> handleBaseException(BaseException e) {
        log.warn("业务异常: code={}, errorCode={}, message={}", e.getCode(), e.getErrorCode(), e.getMessage());
        return RestResult.error(HttpStatus.valueOf(e.getCode()), e.getMessage(), e.getErrorCode());
    }

    /**
     * 处理@Valid注解校验失败的异常
     * @param e MethodArgumentNotValidException
     * @return RestResult
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RestResult<String> handleValidationExceptions(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        log.warn("参数校验失败: {}", errorMessage);
        return RestResult.badRequest(errorMessage);
    }

    /**
     * 处理所有未被捕获的异常，作为最终防线
     * @param e Exception
     * @return RestResult
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RestResult<Void> handleGlobalException(Exception e) {
        log.error("未捕获的系统异常: ", e);
        return RestResult.internalError("服务器内部错误，请稍后重试");
    }
}
