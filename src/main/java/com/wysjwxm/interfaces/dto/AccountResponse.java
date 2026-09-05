package com.wysjwxm.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 账户余额查询响应。
 */
@Getter
@AllArgsConstructor
public class AccountResponse {

    private final Long userId;
    private final BigDecimal balance;
}
