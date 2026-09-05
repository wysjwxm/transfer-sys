package com.wysjwxm.infrastructure.persistence.mybatis;

import com.wysjwxm.domain.account.Account;
import org.apache.ibatis.annotations.Param;

/**
 * 账户表 MyBatis Mapper（SQL 见 resources/mapper/AccountMapper.xml）。
 */
public interface AccountMapper {

    /** 按用户 id 查询并加行锁（供转账事务使用）。 */
    Account findByIdForUpdate(@Param("userId") Long userId);

    /** 按用户 id 查询（无锁）。 */
    Account findById(@Param("userId") Long userId);

    /** 更新余额。 */
    int updateBalance(Account account);
}
