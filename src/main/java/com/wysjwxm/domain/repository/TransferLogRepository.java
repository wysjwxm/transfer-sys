package com.wysjwxm.domain.repository;

import com.wysjwxm.domain.transfer.TransferLog;

/**
 * 转账流水仓储接口（领域层定义，基础设施层实现）。
 */
public interface TransferLogRepository {

    /** 落一条成功转账流水记录。 */
    void insert(TransferLog transferLog);
}
