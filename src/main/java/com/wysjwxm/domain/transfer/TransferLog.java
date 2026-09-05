package com.wysjwxm.domain.transfer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 转账流水：整笔转账的业务记录 + 跨片 TCC 的协调记录（双实例版）。
 *
 * <p>由发起方（转出方所在）节点写入并驱动状态：受理即落一行 TRYING，终态 SUCCESS / FAILED / CANCELLED
 * （或确认阶段网络不确定的 CONFIRMING，留给下期恢复/对账）。失败也会留痕（推翻旧 M0.3"成功才落库"），
 * 因为分布式下需要审计没完成的事。</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class TransferLog {

    private Long id;
    private String txnNo;
    private String requestNo;
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
    private String currency;
    private TransferLogStatus status;
    /** 终态原因（错误码/描述），供审计与对账。 */
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 受理即记：默认状态 TRYING，之后由协调者驱动到终态。 */
    public TransferLog(String txnNo, String requestNo, Long fromUserId, Long toUserId,
                       BigDecimal amount, String currency) {
        this.txnNo = txnNo;
        this.requestNo = requestNo;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.currency = currency;
        this.status = TransferLogStatus.TRYING;
    }
}
