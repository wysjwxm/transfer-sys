package com.wysjwxm.domain.repository;

import com.wysjwxm.domain.transfer.TransferBranch;

/**
 * TCC 分支仓储接口（每节点一张，只记本节点一侧的分支）。
 * 条件更新（confirmIfTryed/cancelIfTryed）是"阶段幂等"的关键：只有分支真的处于 TRYED 才执行账务动作并改状态。
 */
public interface TransferBranchRepository {

    /** 插一行分支（Try 成功后调用；同一事务内连同账务动作提交）。 */
    void insert(TransferBranch branch);

    /** 按业务单号查本侧分支；无则返回 null。 */
    TransferBranch findByTxnNo(String txnNo);

    /** TRYED → CONFIRMED 的原子推进；返回受影响行数（1=本次真的完成了确认，0=早已确认/非 TRYED）。 */
    int confirmIfTryed(String txnNo);

    /** TRYED → CANCELLED 的原子推进；返回受影响行数（1=本次真的完成取消，0=早已取消/非 TRYED）。 */
    int cancelIfTryed(String txnNo);
}
