package com.wysjwxm.domain.exception;

import lombok.Getter;

/**
 * 业务异常：领域层判定"这笔转账不该发生"时抛出，
 * 由接口层统一捕获并转换为错误响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

}
