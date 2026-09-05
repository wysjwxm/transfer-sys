package com.wysjwxm.infrastructure.persistence;

import com.wysjwxm.domain.repository.TransferLogRepository;
import com.wysjwxm.domain.transfer.TransferLog;
import com.wysjwxm.infrastructure.persistence.mybatis.TransferLogMapper;
import org.springframework.stereotype.Repository;

/**
 * 转账流水仓储的 MyBatis 实现。
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
}
