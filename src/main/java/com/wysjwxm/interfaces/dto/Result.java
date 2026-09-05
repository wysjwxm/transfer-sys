package com.wysjwxm.interfaces.dto;

import com.wysjwxm.domain.exception.ErrorCode;
import lombok.Getter;

/**
 * 统一返回结构：code=0 表示成功，非 0 为业务/系统错误码（见 {@link ErrorCode}）。
 * 接口一律 HTTP 200，业务成败看 body.code（国内支付 API 常见风格）。
 */
@Getter
public class Result<T> {

    public static final int SUCCESS_CODE = 0;

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "成功", data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
}
