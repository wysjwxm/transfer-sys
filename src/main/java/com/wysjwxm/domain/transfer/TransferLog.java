package com.wysjwxm.domain.transfer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 转账流水（一笔行内转账，整笔一行）。
 *
 * <p>它是转账的业务记录而非纯日志：后续承担状态流转、查询、冲正/对账锚点等职能。
 * txn_no 为全局唯一业务单号。与"动作"概念的 {@link TransferService} 区分：
 * TransferLog 记录这笔转账本身，TransferService 负责执行转账规则。</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class TransferLog {

    private Long id;
    private String txnNo;
    private String requestNo;
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
    private String currency;
    private TransferLogStatus status;
    private LocalDateTime createTime;

    public TransferLog(String txnNo, String requestNo, Long fromUserId, Long toUserId,
                       BigDecimal amount, String currency) {
        this.txnNo = txnNo;
        this.requestNo = requestNo;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.currency = currency;
        this.status = TransferLogStatus.SUCCESS;
    }
}
