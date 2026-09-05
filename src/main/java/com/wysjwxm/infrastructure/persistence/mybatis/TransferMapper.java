package com.wysjwxm.infrastructure.persistence.mybatis;

import com.wysjwxm.domain.transfer.Transfer;

/**
 * 转账表 MyBatis Mapper（SQL 见 resources/mapper/TransferMapper.xml）。
 */
public interface TransferMapper {

    /** 插入一条成功转账，主键回填到入参对象。 */
    int insert(Transfer transfer);
}
