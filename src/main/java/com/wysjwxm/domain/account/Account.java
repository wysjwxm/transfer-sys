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
 * <p>余额分两态：{@code balance}（可用）+ {@code frozenAmount}（冻结，跨片 TCC Try 预留）。
 * 不变式：总资产 = balance + frozenAmount。
 *
 * <p>业务规则收敛在本类：
 * <ul>
 *   <li>同片转账（无 TCC）：走 {@link #withdraw}/{@link #deposit}，冻结恒为 0，等价于原单余额。</li>
 *   <li>跨片转账（TCC 付款方）：{@link #freeze} 预留 → {@link #confirmFreeze} 真扣 / {@link #unfreeze} 归还。</li>
 * </ul>
 * 余额非负与"冻结不能超额度"均由这些方法保证。
 * 注：Lombok 生成的 setter 仅供持久层（MyBatis）装配对象，业务变动请走上面的方法。</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class Account {

    private Long userId;
    private BigDecimal balance;
    private BigDecimal frozenAmount;

    /** 是否足够扣减 amount（只看可用余额；冻结中的钱不可再花）。 */
    public boolean hasEnough(BigDecimal amount) {
        return balance != null && balance.compareTo(amount) >= 0;
    }

    /** 同片转账扣款：可用余额不足则抛业务异常，调用方事务随之回滚。 */
    public void withdraw(BigDecimal amount) {
        requireAmount(amount);
        if (!hasEnough(amount)) {
            throw new BizException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance = this.balance.subtract(amount);
    }

    /** 同片转账入账：累加可用余额。 */
    public void deposit(BigDecimal amount) {
        requireAmount(amount);
        this.balance = this.balance.add(amount);
    }

    /** TCC Try（付款方）：可用余额扣除并转入冻结——钱还在账上，只是不能花。 */
    public void freeze(BigDecimal amount) {
        requireAmount(amount);
        if (!hasEnough(amount)) {
            throw new BizException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance = this.balance.subtract(amount);
        this.frozenAmount = this.frozenAmount.add(amount);
    }

    /** TCC Cancel（付款方）：把 Try 预留的冻结解冻归还到可用余额。 */
    public void unfreeze(BigDecimal amount) {
        requireAmount(amount);
        ensureFrozen(amount);
        this.frozenAmount = this.frozenAmount.subtract(amount);
        this.balance = this.balance.add(amount);
    }

    /** TCC Confirm（付款方）：冻结转真扣——钱从账上离开（转给收款方）。 */
    public void confirmFreeze(BigDecimal amount) {
        requireAmount(amount);
        ensureFrozen(amount);
        this.frozenAmount = this.frozenAmount.subtract(amount);
    }

    private void requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.INVALID_AMOUNT);
        }
    }

    /** 解冻/确认金额超过冻结金额属编程错误（协调者/状态机 bug），应尽快暴露而非静默吞掉。 */
    private void ensureFrozen(BigDecimal amount) {
        if (frozenAmount == null || frozenAmount.compareTo(amount) < 0) {
            throw new IllegalStateException("冻结金额不足: userId=" + userId + ", need=" + amount + ", frozen=" + frozenAmount);
        }
    }
}
