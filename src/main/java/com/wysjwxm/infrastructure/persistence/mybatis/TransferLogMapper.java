package com.wysjwxm.infrastructure.persistence.mybatis;

import com.wysjwxm.domain.transfer.TransferLog;

/**
 * 转账流水表 MyBatis Mapper（SQL 见 resources/mapper/TransferLogMapper.xml）。
 */
public interface TransferLogMapper {

    /** 插入一条成功转账流水，主键回填到入参对象。 */
    int insert(TransferLog transferLog);
}
