package com.wysjwxm.infrastructure.persistence.mybatis;

import com.wysjwxm.domain.transfer.TransferBranch;
import org.apache.ibatis.annotations.Param;

/**
 * TCC 分支表 MyBatis Mapper（SQL 见 resources/mapper/TransferBranchMapper.xml）。
 */
public interface TransferBranchMapper {

    /** 插一行分支（Try 成功后调用）。 */
    int insert(TransferBranch branch);

    TransferBranch findByTxnNo(@Param("txnNo") String txnNo);

    /** TRYED → CONFIRMED 条件推进，返回受影响行数。 */
    int confirmIfTryed(@Param("txnNo") String txnNo);

    /** TRYED → CANCELLED 条件推进，返回受影响行数。 */
    int cancelIfTryed(@Param("txnNo") String txnNo);
}
