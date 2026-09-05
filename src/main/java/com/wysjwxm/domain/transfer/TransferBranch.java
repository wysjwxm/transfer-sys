package com.wysjwxm.domain.transfer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TCC 分支记录（每节点一张表，只记本节点账户参与的那一侧）。
 * 它既是一侧分支的状态机，也是 Confirm/Cancel 的上下文（txn_no + role + amount）。
 * SQL 见 resources/mapper/TransferBranchMapper.xml。
 */
@Getter
@Setter
@NoArgsConstructor
public class TransferBranch {

    private Long id;
    /** 全局唯一业务号，两侧同号。 */
    private String txnNo;
    /** 本节点这侧是哪个账户。 */
    private Long userId;
    /** 本节点是付款方还是收款方。 */
    private BranchRole role;
    private BigDecimal amount;
    private BranchStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public TransferBranch(String txnNo, Long userId, BranchRole role, BigDecimal amount, BranchStatus status) {
        this.txnNo = txnNo;
        this.userId = userId;
        this.role = role;
        this.amount = amount;
        this.status = status;
    }
}
