package com.wysjwxm.application;

import com.wysjwxm.domain.transfer.BranchRole;

import java.math.BigDecimal;

/**
 * "打到对端 TCC 参与者"的出站端口（应用层声明，基础设施层用 HTTP 实现，见 HttpTccPeer）。
 *
 * <p>跨片转账的协调者 = 转出方所在节点，它只把<b>收款方所在节点</b>当作对端：
 * 收方分支是"空 Try/Cancel"（收方不冻结、入账不失败），confirm 才真入账。
 * 端口三方法与本地 {@code TccParticipantService} 三阶段一一对应——"本地 vs 远程同一语义"。</p>
 *
 * <p>返回约定（呼应 {@code Result.code}）：业务失败返回非 0 错误码，0=成功；
 * 只有<b>联系不上对端/超时这类传输层失败</b>才抛 {@link PeerCallException}。
 * 这样协调者能区分"对端明确拒绝"（结果确定 → 可回滚成 CANCELLED）
 * 与"结果未知"（不能确定资金状态 → 置 CONFIRMING 遗留，留给下期恢复/对账）。</p>
 */
public interface TccPeer {

    /** 请求对端执行一次 Try（收方：校验存在 + 登记分支）。返回业务码（0=成功）。 */
    int tryBranch(String txnNo, BranchRole role, Long userId, BigDecimal amount);

    /** 请求对端执行一次 Confirm（收方：真入账）。返回业务码（0=成功）。 */
    int confirmBranch(String txnNo, BranchRole role, Long userId, BigDecimal amount);

    /** 请求对端执行一次 Cancel（收方：空操作/空回滚占位）。返回业务码（0=成功）。 */
    int cancelBranch(String txnNo, BranchRole role, Long userId, BigDecimal amount);
}
