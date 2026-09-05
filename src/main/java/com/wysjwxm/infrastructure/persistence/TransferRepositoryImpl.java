package com.wysjwxm.infrastructure.persistence;

import com.wysjwxm.domain.repository.TransferRepository;
import com.wysjwxm.domain.transfer.Transfer;
import com.wysjwxm.infrastructure.persistence.mybatis.TransferMapper;
import org.springframework.stereotype.Repository;

/**
 * 转账仓储的 MyBatis 实现。
 */
@Repository
public class TransferRepositoryImpl implements TransferRepository {

    private final TransferMapper transferMapper;

    public TransferRepositoryImpl(TransferMapper transferMapper) {
        this.transferMapper = transferMapper;
    }

    @Override
    public void insert(Transfer transfer) {
        transferMapper.insert(transfer);
    }
}
