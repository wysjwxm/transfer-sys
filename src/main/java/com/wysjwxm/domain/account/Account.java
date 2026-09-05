package com.wysjwxm.domain.account;

import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 账户聚合根。
 *
 * <p>余额是账户的唯一可变状态，业务规则收敛在本类：
 * 扣款前校验余额充足（不足抛 {@link BizException}），入账累加余额。
 * 余额非负由这两个方法保证。
 * 注：Lombok 生成的 setter 仅供持久层（MyBatis）装配对象，业务变动请走 withdraw/deposit。</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class Account {

    private Long userId;
    private BigDecimal balance;

    /** 是否足够扣减 amount。 */
    public boolean hasEnough(BigDecimal amount) {
        return balance != null && balance.compareTo(amount) >= 0;
    }

    /** 扣款：余额不足则抛出业务异常，调用方事务随之回滚。 */
    public void withdraw(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.INVALID_AMOUNT);
        }
        if (!hasEnough(amount)) {
            throw new BizException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance = this.balance.subtract(amount);
    }

    /** 入账：累加余额。 */
    public void deposit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.INVALID_AMOUNT);
        }
        this.balance = this.balance.add(amount);
    }
}
