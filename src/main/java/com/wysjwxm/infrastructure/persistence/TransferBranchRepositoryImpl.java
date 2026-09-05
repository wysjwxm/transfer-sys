package com.wysjwxm.infrastructure.persistence;

import com.wysjwxm.domain.repository.TransferBranchRepository;
import com.wysjwxm.domain.transfer.TransferBranch;
import com.wysjwxm.infrastructure.persistence.mybatis.TransferBranchMapper;
import org.springframework.stereotype.Repository;

/**
 * TCC 分支仓储的 MyBatis 实现（每节点一张 tcc_branch 表）。
 */
@Repository
public class TransferBranchRepositoryImpl implements TransferBranchRepository {

    private final TransferBranchMapper branchMapper;

    public TransferBranchRepositoryImpl(TransferBranchMapper branchMapper) {
        this.branchMapper = branchMapper;
    }

    @Override
    public void insert(TransferBranch branch) {
        branchMapper.insert(branch);
    }

    @Override
    public TransferBranch findByTxnNo(String txnNo) {
        return branchMapper.findByTxnNo(txnNo);
    }

    @Override
    public int confirmIfTryed(String txnNo) {
        return branchMapper.confirmIfTryed(txnNo);
    }

    @Override
    public int cancelIfTryed(String txnNo) {
        return branchMapper.cancelIfTryed(txnNo);
    }
}
