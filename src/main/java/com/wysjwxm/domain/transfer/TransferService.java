package com.wysjwxm.domain.transfer;

import com.wysjwxm.domain.account.Account;
import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 转账领域服务：表达"一次转账"这条业务规则，纯内存操作、不含任何持久化。
 *
 * <p>行内转账跨越两个账户聚合，且必须强一致（单事务内扣+加），
 * 这是 DDD"跨聚合应最终一致"原则的例外——由应用层负责事务与加锁，
 * 本服务只负责规则判定与余额变动。</p>
 */
@Service
public class TransferService {

    /**
     * 执行一笔转账的领域动作：从 from 扣款、给 to 入账。
     * 任一校验不通过（金额非法/转给自己/余额不足）均抛出 {@link BizException}。
     */
    public void transfer(Account from, Account to, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.INVALID_AMOUNT);
        }
        if (from == null || to == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (from.getUserId().equals(to.getUserId())) {
            throw new BizException(ErrorCode.SAME_ACCOUNT);
        }
        from.withdraw(amount);
        to.deposit(amount);
    }
}
