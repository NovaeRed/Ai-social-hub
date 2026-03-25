package cn.redture.common.exception.businessException;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 资源未找到异常
 * <p>
 * 通用的资源未找到异常，可用于用户、日程、文件等任何资源。
 */
public class ResourceNotFoundException extends BaseException {

    /**
     * 构造一个默认消息的“资源未找到”异常
     * @param resourceName 资源名称，例如 "用户"
     */
    public ResourceNotFoundException(String resourceName) {
        super(HttpStatus.NOT_FOUND, String.format("%s不存在", resourceName), ErrorCodes.RESOURCE_NOT_FOUND);
    }
}

