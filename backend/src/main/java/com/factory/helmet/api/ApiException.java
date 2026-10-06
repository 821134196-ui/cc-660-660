package com.factory.helmet.api;

/** 业务异常：携带 HTTP 状态码与中文提示，由 ApiExceptionMapper 统一输出 {"error": msg}。 */
public class ApiException extends RuntimeException {

    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, message);
    }
}
