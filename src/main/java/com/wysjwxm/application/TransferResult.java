package com.wysjwxm.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 转账用例的结果（给调用方回执的业务单号）。
 */
@Getter
@AllArgsConstructor
public class TransferResult {

    private final String txnNo;
}
