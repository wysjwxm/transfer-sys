package com.wysjwxm.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 发起转账的请求。
 * amount 用字符串承载，避免 JSON 数值经 double 丢失精度，接口层解析为 BigDecimal。
 */
@Getter
@Setter
public class TransferRequest {

    private Long fromUserId;
    private Long toUserId;
    /** 转账金额，如 "100.00"。 */
    private String amount;
    /** 请求方幂等键（本期可选，仅落库）。 */
    private String requestNo;
}
