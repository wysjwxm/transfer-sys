package com.wysjwxm.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 一次转账用例的输入（应用层内部对象，由接口层把请求 DTO 转换而来）。
 */
@Getter
@AllArgsConstructor
public class TransferCommand {

    private final Long fromUserId;
    private final Long toUserId;
    private final BigDecimal amount;
    private final String requestNo;
}
