package com.wysjwxm.domain.exception;

import lombok.Getter;

/**
 * 业务错误码。code=0 表示成功（见 Result），其余为各类业务/系统错误。
 * 消息面向接口返回，可直接透出给调用方。
 */
@Getter
public enum ErrorCode {

    SYSTEM_ERROR(5000, "系统繁忙，请稍后再试"),
    ACCOUNT_NOT_FOUND(1001, "账户不存在"),
    INSUFFICIENT_BALANCE(1002, "余额不足"),
    INVALID_AMOUNT(1003, "转账金额必须大于 0"),
    SAME_ACCOUNT(1004, "不能转账给自己"),
    INVALID_REQUEST(1005, "请求参数非法");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
