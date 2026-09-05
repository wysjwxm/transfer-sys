package com.wysjwxm.domain.transfer;

/**
 * TCC 分支在本节点一侧的角色。
 * 收款方 Try 不冻结（入账不会失败），是"空 Try"——这是 TCC 里该强调的点。
 */
public enum BranchRole {

    /** 付款方：Try 时冻结金额，Confirm 真扣 / Cancel 解冻。 */
    PAYER,
    /** 收款方：Try 仅登记并校验存在，Confirm 入账 / Cancel 空操作。 */
    PAYEE
}
