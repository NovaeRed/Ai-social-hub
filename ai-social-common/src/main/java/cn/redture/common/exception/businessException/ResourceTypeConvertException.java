package cn.redture.common.exception.businessException;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 资源类型转换异常
 * <p>
 * 当尝试将资源从一种类型转换为另一种类型时，如果转换不合法或不可行，则抛出此异常。
 * 例如，尝试将一个文本文件转换为图像文件时，如果该操作不被支持，则会抛出此异常。
 */
public class ResourceTypeConvertException extends BaseException {

    public ResourceTypeConvertException() {
        super(HttpStatus.BAD_REQUEST, "资源类型转换失败", ErrorCodes.RESOURCE_TYPE_CONVERT);
    }

    public ResourceTypeConvertException(String message) {
        super(HttpStatus.BAD_REQUEST, message, ErrorCodes.RESOURCE_TYPE_CONVERT);
    }

    public ResourceTypeConvertException(String message, String errorCode) {
        super(HttpStatus.BAD_REQUEST, message, errorCode);
    }

    public ResourceTypeConvertException(int code, String message) {
        super(code, message, ErrorCodes.RESOURCE_TYPE_CONVERT);
    }

    public ResourceTypeConvertException(int code, String message, String errorCode) {
        super(code, message, errorCode);
    }
}
