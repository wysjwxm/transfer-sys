package com.wysjwxm.infrastructure.persistence;

import com.wysjwxm.domain.account.Account;
import com.wysjwxm.domain.repository.AccountRepository;
import com.wysjwxm.infrastructure.persistence.mybatis.AccountMapper;
import org.springframework.stereotype.Repository;

/**
 * 账户仓储的 MyBatis 实现：领域接口定义在 domain，本类在基础设施层兜底落地。
 */
@Repository
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountMapper accountMapper;

    public AccountRepositoryImpl(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Override
    public Account findByIdForUpdate(Long userId) {
        return accountMapper.findByIdForUpdate(userId);
    }

    @Override
    public Account findById(Long userId) {
        return accountMapper.findById(userId);
    }

    @Override
    public void update(Account account) {
        accountMapper.update(account);
    }
}
