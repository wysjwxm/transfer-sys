package com.wysjwxm.infrastructure.persistence;

import com.wysjwxm.domain.repository.TransferLogRepository;
import com.wysjwxm.domain.transfer.TransferLog;
import com.wysjwxm.domain.transfer.TransferLogStatus;
import com.wysjwxm.infrastructure.persistence.mybatis.TransferLogMapper;
import org.springframework.stereotype.Repository;

/**
 * 转账流水（协调记录）仓储的 MyBatis 实现。
 */
@Repository
public class TransferLogRepositoryImpl implements TransferLogRepository {

    private final TransferLogMapper transferLogMapper;

    public TransferLogRepositoryImpl(TransferLogMapper transferLogMapper) {
        this.transferLogMapper = transferLogMapper;
    }

    @Override
    public void insert(TransferLog transferLog) {
        transferLogMapper.insert(transferLog);
    }

    @Override
    public TransferLog findByTxnNo(String txnNo) {
        return transferLogMapper.findByTxnNo(txnNo);
    }

    @Override
    public TransferLog findByRequestNo(String requestNo) {
        return transferLogMapper.findByRequestNo(requestNo);
    }

    @Override
    public void updateStatus(String txnNo, TransferLogStatus status, String remark) {
        transferLogMapper.updateStatus(txnNo, status, remark);
    }
}
