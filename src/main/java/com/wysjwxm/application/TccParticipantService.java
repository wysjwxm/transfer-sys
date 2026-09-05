package com.wysjwxm.application;

import com.wysjwxm.domain.account.Account;
import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.domain.repository.AccountRepository;
import com.wysjwxm.domain.repository.TransferBranchRepository;
import com.wysjwxm.domain.transfer.BranchRole;
import com.wysjwxm.domain.transfer.BranchStatus;
import com.wysjwxm.domain.transfer.TransferBranch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * TCC 参与者：对"本节点自己库里的账户"执行一次 Try / Confirm / Cancel。
 *
 * <p>被两处复用——本地付款方由协调者直连（同 JVM 走 Spring 事务），
 * 远端收款方经 {@code /internal/tcc/*} 由 HTTP 到达（本服务即其实现），所以"本地 vs 远程同一语义"。</p>
 *
 * <p>角色语义（TCC 里要讲清的点）：
 * <ul>
 *   <li>{@link BranchRole#PAYER}：Try 真冻结（balance→frozen），Confirm 真扣走，Cancel 解冻归还。</li>
 *   <li>{@link BranchRole#PAYEE}：Try 只锁账户、校验存在并登记分支（<b>不冻结</b>，入账不会失败），
 *       Confirm 才真入账，Cancel 是<b>空操作</b>。</li>
 * </ul>
 * 各阶段自身 @Transactional 单库原子；用 tcc_branch 单行状态机 TRYED→CONFIRMED/CANCELLED 的条件更新
 * 保证三件事：
 * <ul>
 *   <li><b>幂等</b>：重复 Confirm/Cancel 因状态已推进而 no-op，账务动作只做一次（条件更新返回 0 则不再做）；</li>
 *   <li><b>空回滚</b>：Cancel 时根本没有 TRYED 行（Try 从未成功）→ 只插一行 CANCELLED 占位，不做账务动作；</li>
 *   <li><b>悬挂</b>：迟到的 Try 发现已存在 CANCELLED 占位 → 拒绝冻结，防止"再冻结一笔没人会确认/取消的钱"。</li>
 * </ul>
 */
@Service
public class TccParticipantService {

    private final AccountRepository accountRepository;
    private final TransferBranchRepository branchRepository;

    public TccParticipantService(AccountRepository accountRepository,
                                 TransferBranchRepository branchRepository) {
        this.accountRepository = accountRepository;
        this.branchRepository = branchRepository;
    }

    /** Try：付款方冻结 / 收款方登记。成功则落 TRYED 行。业务拒绝抛 {@link BizException}。 */
    @Transactional
    public void tryBranch(String txnNo, Long userId, BranchRole role, BigDecimal amount) {
        requireBranchParam(txnNo, userId, role, amount);

        TransferBranch existing = branchRepository.findByTxnNo(txnNo);
        if (existing != null) {
            if (existing.getStatus() == BranchStatus.CANCELLED) {
                // 悬挂：本单已被 Cancel（空回滚占位）过，迟到的 Try 不许再冻，钱无人认领
                throw new BizException(ErrorCode.INVALID_REQUEST, "分支已取消，拒绝迟到的 Try(防悬挂)");
            }
            if (existing.getStatus() == BranchStatus.CONFIRMED) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "分支已确认，重复 Try 非法");
            }
            // 幂等：已 TRYED，说明这次 Try 之前已成功，直接当作成功
            return;
        }

        Account account = accountRepository.findByIdForUpdate(userId);
        if (account == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (role == BranchRole.PAYER) {
            account.freeze(amount);          // 余额不足在此抛 INSUFFICIENT_BALANCE
            accountRepository.update(account);
        }
        // PAYEE：收款方 Try 不冻结，仅登记
        branchRepository.insert(new TransferBranch(txnNo, userId, role, amount, BranchStatus.TRYED));
    }

    /** Confirm：付款方冻结转真扣 / 收款方真入账。只对 TRYED 分支执行一次，幂等可重放。 */
    @Transactional
    public void confirmBranch(String txnNo, Long userId, BranchRole role, BigDecimal amount) {
        requireBranchParam(txnNo, userId, role, amount);

        TransferBranch existing = branchRepository.findByTxnNo(txnNo);
        if (existing == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Confirm 无 Try 分支");
        }
        if (existing.getStatus() == BranchStatus.CANCELLED) {
            // 已空回滚/取消过：绝不能对一笔"已放弃"的交易入账（可能重复入账）
            throw new BizException(ErrorCode.INVALID_REQUEST, "分支已取消，禁止 Confirm");
        }
        if (existing.getStatus() == BranchStatus.CONFIRMED) {
            return;                          // 幂等：早已确认，no-op
        }

        // 原子推进 TRYED→CONFIRMED；返回 1=本次抢到执行权（账务动作只做一次），0=被并发先确认
        int rows = branchRepository.confirmIfTryed(txnNo);
        if (rows == 1) {
            Account account = accountRepository.findByIdForUpdate(userId);
            if (account == null) {
                throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);   // 抛错回滚状态推进，保持 TRYED
            }
            if (role == BranchRole.PAYER) {
                account.confirmFreeze(amount);
            } else {
                account.deposit(amount);      // 收款方：Try 时未入账，Confirm 才加钱
            }
            accountRepository.update(account);
        }
    }

    /** Cancel：付款方解冻归还 / 收款方空操作。幂等 + 空回滚见类注释。 */
    @Transactional
    public void cancelBranch(String txnNo, Long userId, BranchRole role, BigDecimal amount) {
        requireBranchParam(txnNo, userId, role, amount);

        TransferBranch existing = branchRepository.findByTxnNo(txnNo);
        if (existing == null) {
            // 空回滚：Try 从未成功（本侧无账务变动）→ 只登记 CANCELLED 占位，防止迟到的 Try 再冻结
            branchRepository.insert(new TransferBranch(txnNo, userId, role, amount, BranchStatus.CANCELLED));
            return;
        }
        if (existing.getStatus() == BranchStatus.CANCELLED) {
            return;                          // 幂等：早已取消，no-op
        }
        if (existing.getStatus() == BranchStatus.CONFIRMED) {
            // Confirm 之后再来 Cancel：顺序错乱（协调者 bug），资金已动不能反悔，抛错暴露
            throw new BizException(ErrorCode.INVALID_REQUEST, "分支已确认，禁止 Cancel");
        }

        int rows = branchRepository.cancelIfTryed(txnNo);
        if (rows == 1) {
            if (role == BranchRole.PAYER) {
                Account account = accountRepository.findByIdForUpdate(userId);
                if (account == null) {
                    throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
                }
                account.unfreeze(amount);    // 解冻归还可用余额
                accountRepository.update(account);
            }
            // PAYEE：Try 未入账，Cancel 无账务动作，仅状态推进
        }
    }

    private void requireBranchParam(String txnNo, Long userId, BranchRole role, BigDecimal amount) {
        if (txnNo == null || txnNo.trim().isEmpty() || userId == null || userId <= 0
                || role == null || amount == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
    }
}
