package com.wysjwxm.domain.repository;

import com.wysjwxm.domain.transfer.Transfer;

/**
 * 转账仓储接口（领域层定义，基础设施层实现）。
 */
public interface TransferRepository {

    /** 落一条成功转账记录。 */
    void insert(Transfer transfer);
}
