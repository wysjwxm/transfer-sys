package com.wysjwxm.infrastructure.persistence.mybatis;

import com.wysjwxm.domain.transfer.TransferLog;
import com.wysjwxm.domain.transfer.TransferLogStatus;
import org.apache.ibatis.annotations.Param;

/**
 * 转账流水（协调记录）表 MyBatis Mapper（SQL 见 resources/mapper/TransferLogMapper.xml）。
 */
public interface TransferLogMapper {

    /** 插入一条受理记录（status=TRYING），主键回填到入参对象。 */
    int insert(TransferLog transferLog);

    TransferLog findByTxnNo(@Param("txnNo") String txnNo);

    TransferLog findByRequestNo(@Param("requestNo") String requestNo);

    int updateStatus(@Param("txnNo") String txnNo,
                     @Param("status") TransferLogStatus status,
                     @Param("remark") String remark);
}
