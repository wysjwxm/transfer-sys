package com.wysjwxm.domain.repository;

import com.wysjwxm.domain.transfer.TransferLog;
import com.wysjwxm.domain.transfer.TransferLogStatus;

/**
 * 转账流水（协调记录）仓储接口。只由发起方节点写入与驱动状态。
 */
public interface TransferLogRepository {

    /** 落一条受理记录（status=TRYING）。 */
    void insert(TransferLog transferLog);

    /** 按业务单号查。 */
    TransferLog findByTxnNo(String txnNo);

    /** 按请求方幂等键查（用于 request_no 判重：同号请求回原 txn_no）。 */
    TransferLog findByRequestNo(String requestNo);

    /** 推进状态与终态原因（remark 可为 null）。 */
    void updateStatus(String txnNo, TransferLogStatus status, String remark);
}
