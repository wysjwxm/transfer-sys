package com.wysjwxm.application;

import com.wysjwxm.domain.account.Account;
import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.domain.repository.AccountRepository;
import com.wysjwxm.domain.repository.TransferLogRepository;
import com.wysjwxm.domain.transfer.TransferLogStatus;
import com.wysjwxm.domain.transfer.TransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 同片转账的本地强一致事务（扣 + 加 + 流水置成功，单库一个事务内原子完成）。
 *
 * <p>单独成 bean 而非塞进 {@link TransferAppService}，是为了让 {@code @Transactional} 生效：
 * 若事务方法写在协调者类里再由同类的 transfer(...) 自调用，会绕过 Spring 代理，
 * 事务就不生效了（协调者本身必须无事务——它要编排多个独立小事务 + 远程调用）。</p>
 *
 * <p>任何业务失败（金额非法/自转/余额不足/收款不存在）抛 {@link BizException} 整体回滚：
 * 事务内那条"置 SUCCESS"也会一起回滚，TRYING 行保留在已提交的受理事务里，
 * 由协调者另起小事务改成 FAILED。</p>
 */
@Service
public class LocalTransferRunner {

    private final AccountRepository accountRepository;
    private final TransferService transferService;
    private final TransferLogRepository transferLogRepository;

    public LocalTransferRunner(AccountRepository accountRepository,
                               TransferService transferService,
                               TransferLogRepository transferLogRepository) {
        this.accountRepository = accountRepository;
        this.transferService = transferService;
        this.transferLogRepository = transferLogRepository;
    }

    /** 同片转账主事务：锁两账户(升序) → 领域扣加 → 落余额 → 流水置 SUCCESS。 */
    @Transactional
    public void runAndMarkSuccess(String txnNo, Long fromUserId, Long toUserId, BigDecimal amount) {
        // 两账户按 user_id 升序加锁，避免并发互转时交叉等待成环（死锁）
        Long lowerId = Math.min(fromUserId, toUserId);
        Long higherId = Math.max(fromUserId, toUserId);

        Account lower = accountRepository.findByIdForUpdate(lowerId);
        if (lower == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        Account higher = fromUserId.equals(toUserId) ? lower
                : accountRepository.findByIdForUpdate(higherId);
        if (higher == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        Account from = (lower.getUserId().equals(fromUserId)) ? lower : higher;
        Account to = (from == lower) ? higher : lower;

        // 领域动作：金额非法/转自己/余额不足 均在此抛 BizException，整体回滚
        transferService.transfer(from, to, amount);

        accountRepository.update(from);
        accountRepository.update(to);
        transferLogRepository.updateStatus(txnNo, TransferLogStatus.SUCCESS, null);
    }
}
