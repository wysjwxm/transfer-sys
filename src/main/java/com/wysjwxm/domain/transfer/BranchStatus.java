package com.wysjwxm.domain.transfer;

/**
 * TCC 分支状态。用单行状态机保证各阶段幂等/不重放：
 * <ul>
 *   <li>Try 成功落 TRYED；</li>
 *   <li>Confirm 仅在 TRYED→CONFIRMED 时执行账务动作（重复 Confirm 落空 → 幂等 no-op）；</li>
 *   <li>Cancel 仅在 TRYED→CANCELLED 时解冻；无 TRYED 行时插一行 CANCELLED 作"空回滚"记录，防迟到 Try 再冻（悬挂）。</li>
 * </ul>
 */
public enum BranchStatus {

    /** 已 Try（付款方已冻结 / 收款方已登记）。 */
    TRYED,
    /** 已 Confirm（资金已真扣走 / 已入账）。 */
    CONFIRMED,
    /** 已 Cancel（付款方已解冻归还 / 收款方空操作；也可能是"空回滚"占位）。 */
    CANCELLED
}
