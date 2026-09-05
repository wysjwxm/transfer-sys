package com.wysjwxm.application;

import com.wysjwxm.domain.account.Account;
import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.domain.repository.AccountRepository;
import com.wysjwxm.domain.transfer.BranchRole;
import com.wysjwxm.infrastructure.config.ShardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 转账用例的应用服务（协调者）：归属守卫 + 同/跨片分流 + 跨片 TCC 编排。
 *
 * <p>本类自身<b>不开事务</b>，把需要事务的方法委托给协作 bean
 * （{@link TransferLogRecorder} / {@link LocalTransferRunner} / {@link TccParticipantService}），
 * 两个原因：
 * <ol>
 *   <li>避免自调用绕 Spring 代理——事务方法写在别的类里由本类调用，@Transactional 才生效；</li>
 *   <li>跨片编排横跨"本地多个小事务 + 对端 HTTP 调用"，不能包在单库事务里
 *       （远程参与方不在本库事务边界内）。</li>
 * </ol>
 * 终态判别贯穿于此：fail→markFailed（未预留）、cancel→markCancelled（曾预留已释放）、
 * 不确定→markConfirming（留给下期恢复）。
 */
@Service
public class TransferAppService {

    private static final Logger log = LoggerFactory.getLogger(TransferAppService.class);

    private final AccountRepository accountRepository;
    private final TransferLogRecorder transferLogRecorder;
    private final LocalTransferRunner localTransferRunner;
    private final TccParticipantService tccParticipantService;
    private final TccPeer tccPeer;
    private final ShardProperties shardProperties;

    public TransferAppService(AccountRepository accountRepository,
                              TransferLogRecorder transferLogRecorder,
                              LocalTransferRunner localTransferRunner,
                              TccParticipantService tccParticipantService,
                              TccPeer tccPeer,
                              ShardProperties shardProperties) {
        this.accountRepository = accountRepository;
        this.transferLogRecorder = transferLogRecorder;
        this.localTransferRunner = localTransferRunner;
        this.tccParticipantService = tccParticipantService;
        this.tccPeer = tccPeer;
        this.shardProperties = shardProperties;
    }

    /** 发起一笔转账：同片走本地强一致，跨片走 TCC。 */
    public TransferResult transfer(TransferCommand command) {
        requireValid(command);

        Long fromId = command.getFromUserId();
        Long toId = command.getToUserId();
        BigDecimal amount = command.getAmount();

        // 1. 归属守卫：转出方账户必须在本节点（协调者=转出方所在节点），打错节点不转发、直接指引
        if (shardOf(fromId) != shardProperties.getNodeId()) {
            throw new BizException(ErrorCode.WRONG_NODE);
        }

        // 2. 受理落行（TRYING）；request_no 幂等命中 → 回原单号，不再执行
        TransferLogRecorder.Start start =
                transferLogRecorder.recordStarted(fromId, toId, amount, command.getRequestNo());
        if (start.isReplayed()) {
            log.info("request_no 幂等命中，回原单号 txnNo={}", start.getTxnNo());
            return new TransferResult(start.getTxnNo());
        }
        String txnNo = start.getTxnNo();

        // 3. 分流：同一分库 → 本地事务强一致；跨分库 → TCC
        if (shardOf(fromId) == shardOf(toId)) {
            transferSameShard(txnNo, fromId, toId, amount);
        } else {
            transferCrossShard(txnNo, fromId, toId, amount);
        }
        return new TransferResult(txnNo);
    }

    /** 同片：本地强一致。失败即 FAILED（从未预留）。 */
    private void transferSameShard(String txnNo, Long fromId, Long toId, BigDecimal amount) {
        try {
            localTransferRunner.runAndMarkSuccess(txnNo, fromId, toId, amount);
        } catch (BizException e) {
            transferLogRecorder.markFailed(txnNo, e.getErrorCode());
            throw e;
        } catch (RuntimeException e) {
            log.error("同片转账异常 txnNo={}", txnNo, e);
            transferLogRecorder.markFailed(txnNo, ErrorCode.SYSTEM_ERROR);
            throw e;
        }
    }

    /** 跨片 TCC：付款方冻结成功前失败 → FAILED；之后对端失败 → 解冻 → CANCELLED；确认不确定 → CONFIRMING。 */
    private void transferCrossShard(String txnNo, Long fromId, Long toId, BigDecimal amount) {
        // —— Phase 1: Try 付款方（本地，先冻）——
        try {
            tccParticipantService.tryBranch(txnNo, fromId, BranchRole.PAYER, amount);
        } catch (BizException e) {
            // 未发生任何冻结（余额不足/账户不存在/金额非法 都在冻结动作里抛）→ FAILED
            transferLogRecorder.markFailed(txnNo, e.getErrorCode());
            throw e;
        } catch (RuntimeException e) {
            log.error("跨片 Try 付款方异常 txnNo={}", txnNo, e);
            transferLogRecorder.markFailed(txnNo, ErrorCode.SYSTEM_ERROR);
            throw e;
        }

        // —— Phase 2: Try 收款方（远程）。此刻起付款方钱已冻，"曾预留"成立——
        int payeeTryCode;
        try {
            payeeTryCode = tccPeer.tryBranch(txnNo, BranchRole.PAYEE, toId, amount);
        } catch (PeerCallException e) {
            log.warn("收款方 Try 网络失败 txnNo={}，将解冻付款方", txnNo, e);
            compensateCancel(txnNo, fromId, amount, "对端不可达，付款方已解冻");
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        if (payeeTryCode != 0) {
            ErrorCode payeeError = ErrorCode.fromCode(payeeTryCode);
            log.warn("收款方 Try 被拒 code={} txnNo={}，将解冻付款方", payeeTryCode, txnNo);
            compensateCancel(txnNo, fromId, amount, payeeError.getMessage());
            throw new BizException(payeeError);      // 如 ACCOUNT_NOT_FOUND → API 返回 1001，流水 CANCELLED
        }

        // —— Phase 3: Confirm。付款方本地真扣 → 收款方远程入账 ——
        try {
            tccParticipantService.confirmBranch(txnNo, fromId, BranchRole.PAYER, amount);
        } catch (RuntimeException e) {
            log.error("跨片 Confirm 付款方异常 txnNo={}", txnNo, e);
            transferLogRecorder.markConfirming(txnNo, "付款方确认失败，冻结待恢复");
            throw e;
        }
        int payeeConfirmCode;
        try {
            payeeConfirmCode = tccPeer.confirmBranch(txnNo, BranchRole.PAYEE, toId, amount);
        } catch (PeerCallException e) {
            log.warn("收款方 Confirm 网络失败 txnNo={}，资金状态不确定", txnNo, e);
            transferLogRecorder.markConfirming(txnNo, "收款方确认网络失败，待对账/恢复");
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        if (payeeConfirmCode != 0) {
            log.warn("收款方 Confirm 被拒 code={} txnNo={}，资金状态不确定", payeeConfirmCode, txnNo);
            transferLogRecorder.markConfirming(txnNo, "收款方确认被拒，待对账/恢复");
            throw new BizException(ErrorCode.fromCode(payeeConfirmCode));
        }

        transferLogRecorder.markSuccess(txnNo);
    }

    /**
     * 付款方已冻结、对端明确失败时的补偿：本地 Cancel 解冻 → 流水置 CANCELLED。
     * 解冻本身也失败（极端编程错误）→ 流水置 CONFIRMING 留待恢复，资金滞留冻结不可无声吞掉。
     */
    private void compensateCancel(String txnNo, Long payerId, BigDecimal amount, String remark) {
        try {
            tccParticipantService.cancelBranch(txnNo, payerId, BranchRole.PAYER, amount);
            transferLogRecorder.markCancelled(txnNo, remark);
        } catch (RuntimeException cancelEx) {
            log.error("跨片回滚(解冻付款方)失败 txnNo={}，冻结资金滞留，待下期恢复", txnNo, cancelEx);
            transferLogRecorder.markConfirming(txnNo, "Cancel 解冻失败，冻结滞留待恢复");
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 查询账户（只读）。先做归属守卫：账户不归本节点 → 指引去对端节点。 */
    public Account getAccount(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        if (shardOf(userId) != shardProperties.getNodeId()) {
            throw new BizException(ErrorCode.WRONG_NODE);
        }
        Account account = accountRepository.findById(userId);
        if (account == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private void requireValid(TransferCommand command) {
        if (command == null || command.getFromUserId() == null || command.getToUserId() == null
                || command.getAmount() == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        if (command.getFromUserId() <= 0 || command.getToUserId() <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        // 注意：amount <= 0 不在此拦截——金额非法属"业务失败"，要落 FAILED 流水（无预留即失败）
    }

    /** 分片契约：shardOf(userId) = userId % 2，本节点持有 == node-id 的账户。入参已保证正数。 */
    private static int shardOf(Long userId) {
        return (int) (userId % 2);
    }
}
