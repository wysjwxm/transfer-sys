package com.wysjwxm.interfaces.web;

import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.interfaces.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常兜底：领域异常转业务错误码，未知异常转系统错误并记录日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        return Result.fail(e.getErrorCode());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未预期异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
