package com.wysjwxm.domain.repository;

import com.wysjwxm.domain.account.Account;

/**
 * 账户仓储接口（领域层定义，基础设施层实现——依赖倒置）。
 * 本节点只存本片账户（userId % 2 == node-id）。
 */
public interface AccountRepository {

    /** 按用户 id 加载并加行锁（SELECT ... FOR UPDATE），供转账事务内使用。 */
    Account findByIdForUpdate(Long userId);

    /** 按用户 id 加载（无锁），供余额查询等只读场景使用。 */
    Account findById(Long userId);

    /** 持久化账户余额与冻结金额的变动。 */
    void update(Account account);
}
