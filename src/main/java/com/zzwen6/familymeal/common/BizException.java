package com.zzwen6.familymeal.common;

import org.springframework.http.HttpStatus;

/** 业务异常，统一转为 {@link ApiError}。 */
public class BizException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BizException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static BizException notFound(String what) {
        return new BizException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + "不存在");
    }
    public static BizException forbidden(String message) {
        return new BizException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
    public static BizException badRequest(String message) {
        return new BizException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
    public static BizException conflict(String code, String message) {
        return new BizException(HttpStatus.CONFLICT, code, message);
    }
    public static BizException unauthorized(String message) {
        return new BizException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
