package com.wysjwxm.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 节点间 TCC 分支调用（/internal/tcc/*）的请求体。
 *
 * <p>字段刻意用与转账请求一致的"宽松"形态：amount 用字符串承载避免 double 丢精度，
 * role 用字符串（PAYER/PAYEE）便于跨节点传输，由接口层解析为领域枚举。
 * 生产里节点间还应加调用方鉴权/IP 白名单——这是内网接口，不暴露到公网。</p>
 */
@Getter
@Setter
public class TccBranchRequest {

    /** 全局唯一业务单号（两侧同号）。 */
    private String txnNo;

    /** 本节点这侧的角色：PAYER / PAYEE。 */
    private String role;

    /** 本节点账户 id。 */
    private Long userId;

    /** 金额，如 "100.00"。 */
    private String amount;
}
