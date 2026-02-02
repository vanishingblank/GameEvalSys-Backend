package com.eval.gameeval.models.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用响应VO
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ResponseVO<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 响应码
     * 200: 成功
     * 400: 参数错误
     * 401: 未授权
     * 403: 权限不足
     * 404: 资源不存在
     * 500: 服务器错误
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 构造方法
     */
    public ResponseVO(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应 - 无数据
     */
    public static <T> ResponseVO<T> success() {
        return new ResponseVO<>(200, "操作成功", null);
    }

    /**
     * 成功响应 - 有数据
     */
    public static <T> ResponseVO<T> success(T data) {
        return new ResponseVO<>(200, "操作成功", data);
    }

    /**
     * 成功响应 - 自定义消息
     */
    public static <T> ResponseVO<T> success(String message, T data) {
        return new ResponseVO<>(200, message, data);
    }

    /**
     * 错误响应
     */
    public static <T> ResponseVO<T> error(Integer code, String message) {
        return new ResponseVO<>(code, message, null);
    }

    /**
     * 错误响应 - 默认500
     */
    public static <T> ResponseVO<T> error(String message) {
        return new ResponseVO<>(500, message, null);
    }

    /**
     * 参数错误
     */
    public static <T> ResponseVO<T> badRequest(String message) {
        return new ResponseVO<>(400, message, null);
    }

    /**
     * 未授权
     */
    public static <T> ResponseVO<T> unauthorized(String message) {
        return new ResponseVO<>(401, message, null);
    }

    /**
     * 权限不足
     */
    public static <T> ResponseVO<T> forbidden(String message) {
        return new ResponseVO<>(403, message, null);
    }

    /**
     * 资源不存在
     */
    public static <T> ResponseVO<T> notFound(String message) {
        return new ResponseVO<>(404, message, null);
    }
}
