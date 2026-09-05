package com.wysjwxm.application;

import com.wysjwxm.domain.account.Account;
import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.domain.repository.AccountRepository;
import com.wysjwxm.domain.repository.TransferLogRepository;
import com.wysjwxm.domain.transfer.TransferLog;
import com.wysjwxm.domain.transfer.TransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 转账用例的应用服务（编排 + 事务边界，不承载业务规则）。
 *
 * <p>要点（呼应设计决策）：
 * <ul>
 *   <li>事务边界在这里：扣 A + 加 B + 写转账流水在同一个数据库事务内，强一致（TODO M0.2）。</li>
 *   <li>行锁：两个账户按 user_id 升序加锁（SELECT ... FOR UPDATE），避免并发互转死锁。</li>
 *   <li>规则判定交给领域服务 {@link TransferService} 与聚合 {@link Account}。</li>
 * </ul>
 */
@Service
public class TransferAppService {

    private static final String CURRENCY_CNY = "CNY";
    private static final DateTimeFormatter TXN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final AccountRepository accountRepository;
    private final TransferLogRepository transferLogRepository;
    private final TransferService transferService;

    public TransferAppService(AccountRepository accountRepository,
                              TransferLogRepository transferLogRepository,
                              TransferService transferService) {
        this.accountRepository = accountRepository;
        this.transferLogRepository = transferLogRepository;
        this.transferService = transferService;
    }

    /** 发起一笔行内转账。 */
    @Transactional
    public TransferResult transfer(TransferCommand command) {
        requireValid(command);

        Long fromId = command.getFromUserId();
        Long toId = command.getToUserId();

        // 按升序加锁，两个账户都锁到后再执行领域动作，防止交叉等待成环
        Account lower = accountRepository.findByIdForUpdate(Math.min(fromId, toId));
        if (lower == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        Account higher = (fromId.equals(toId)) ? lower
                : accountRepository.findByIdForUpdate(Math.max(fromId, toId));
        if (higher == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        Account from = (lower.getUserId().equals(fromId)) ? lower : higher;
        Account to = (from == lower) ? higher : lower;

        // 领域动作：金额非法/转自己/余额不足 均在此抛 BizException，整体回滚
        transferService.transfer(from, to, command.getAmount());

        accountRepository.updateBalance(from);
        accountRepository.updateBalance(to);

        TransferLog transferLog = new TransferLog(generateTxnNo(), command.getRequestNo(),
                fromId, toId, command.getAmount(), CURRENCY_CNY);
        transferLogRepository.insert(transferLog);

        return new TransferResult(transferLog.getTxnNo());
    }

    /** 查询账户（只读）。 */
    @Transactional(readOnly = true)
    public Account getAccount(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
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
        if (command.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.INVALID_AMOUNT);
        }
    }

    /** 业务单号：时间戳 + 随机后缀，保证本实例内全局唯一（TODO M0.5）。 */
    private String generateTxnNo() {
        String time = LocalDateTime.now().format(TXN_TIME);
        int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "T" + time + suffix;
    }
}
