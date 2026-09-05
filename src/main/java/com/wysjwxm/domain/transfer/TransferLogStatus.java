package com.wysjwxm.domain.transfer;

import lombok.Getter;

/**
 * 转账流水状态。MVP 阶段行内转账单事务原子完成，恒为 SUCCESS（路线甲）；
 * 预留该枚举，待"请求/幂等"阶段（TODO M0.8）再让状态真正流转。
 */
@Getter
public enum TransferLogStatus {

    SUCCESS("SUCCESS", "成功");

    private final String code;
    private final String desc;

    TransferLogStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
