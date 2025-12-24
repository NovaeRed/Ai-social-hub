package cn.redture.common.pojo.model;

import lombok.Data;
import org.springframework.http.HttpStatus;

/**
 * 统一API响应结果封装
 *
 * @param <T> 响应数据的类型
 */
@Data
public class RestResult<T> {

    /**
     * 业务状态码，通常与HTTP状态码一致
     */
    private int code;

    /**
     * 响应消息，成功时为 "success"，失败时为错误信息
     */
    private String msg;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 业务错误码，例如 REFRESH_TOKEN_EXPIRED，可选
     */
    private String errorCode;

    /**
     * 内部使用的构造函数
     */
    private RestResult(int code, String msg, T data) {
        this(code, msg, data, null);
    }

    private RestResult(int code, String msg, T data, String errorCode) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.errorCode = errorCode;
    }

    // --- 成功响应 ---

    /**
     * 成功 (200 OK)
     *
     * @param data 响应数据
     * @return RestResult 实例
     */
    public static <T> RestResult<T> success(T data) {
        return new RestResult<>(HttpStatus.OK.value(), "success", data);
    }

    /**
     * 成功，无数据返回 (200 OK)
     *
     * @return RestResult 实例
     */
    public static <T> RestResult<T> success() {
        return success(null);
    }

    /**
     * 成功，自定义消息 (200 OK)
     *
     * @param message 自定义消息
     * @param data    响应数据
     * @return RestResult 实例
     */
    public static <T> RestResult<T> success(String message, T data) {
        return new RestResult<>(HttpStatus.OK.value(), message, data);
    }

    /**
     * 资源创建成功 (201 Created)
     *
     * @param data 创建的资源
     * @return RestResult 实例
     */
    public static <T> RestResult<T> created(T data) {
        return new RestResult<>(HttpStatus.CREATED.value(), "success", data);
    }

    /**
     * 请求已接受，正在处理 (202 Accepted)
     *
     * @param data 通常是任务ID等信息
     * @return RestResult 实例
     */
    public static <T> RestResult<T> accepted(T data) {
        return new RestResult<>(HttpStatus.ACCEPTED.value(), "success", data);
    }

    /**
     * 操作成功，无内容返回 (204 No Content)
     *
     * @return RestResult 实例
     */
    public static <T> RestResult<T> noContent() {
        // 注意：在Spring MVC中，返回ResponseEntity.noContent().build()是更标准的做法，
        // 但为了统一返回结构体，这里提供一个code为204的RestResult。
        return new RestResult<>(HttpStatus.NO_CONTENT.value(), "success", null);
    }

    // --- 失败响应 ---

    /**
     * 通用错误响应
     *
     * @param status  HTTP状态
     * @param message 错误信息
     * @return RestResult 实例
     */
    public static <T> RestResult<T> error(HttpStatus status, String message) {
        return error(status, message, null);
    }

    public static <T> RestResult<T> error(HttpStatus status, String message, String errorCode) {
        return new RestResult<>(status.value(), message, null, errorCode);
    }

    /**
     * 参数无效 (400 Bad Request)
     *
     * @param message 错误信息
     * @return RestResult 实例
     */
    public static <T> RestResult<T> badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 未授权 (401 Unauthorized)
     *
     * @param message 错误信息
     * @return RestResult 实例
     */
    public static <T> RestResult<T> unauthorized(String message) {
        return error(HttpStatus.UNAUTHORIZED, message);
    }

    /**
     * 禁止访问 (403 Forbidden)
     *
     * @param message 错误信息
     * @return RestResult 实例
     */
    public static <T> RestResult<T> forbidden(String message) {
        return error(HttpStatus.FORBIDDEN, message);
    }

    /**
     * 资源未找到 (404 Not Found)
     *
     * @param message 错误信息
     * @return RestResult 实例
     */
    public static <T> RestResult<T> notFound(String message) {
        return error(HttpStatus.NOT_FOUND, message);
    }

    /**
     * 资源冲突 (409 Conflict)
     *
     * @param message 错误信息
     * @return RestResult 实例
     */
    public static <T> RestResult<T> conflict(String message) {
        return error(HttpStatus.CONFLICT, message);
    }

    /**
     * 服务器内部错误 (500 Internal Server Error)
     *
     * @param message 错误信息
     * @return RestResult 实例
     */
    public static <T> RestResult<T> internalError(String message) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
