package com.wysjwxm.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发起转账的响应回执。
 */
@Getter
@AllArgsConstructor
public class TransferResponse {

    /** 全局唯一业务单号。 */
    private final String txnNo;
}
