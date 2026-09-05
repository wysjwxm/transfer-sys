package com.wysjwxm.domain.transfer;

import lombok.Getter;

/**
 * 转账流水（兼跨片 TCC 协调记录）的状态。
 *
 * <p>受理即落一行 TRYING，随后驱动到终态。FAILED 与 CANCELLED 的判别口径：
 * 是否发生过资金冻结（预留）——没冻就失败 → FAILED（同片失败、参数/余额不足/自转、付款方冻结前失败）；
 * 冻过又解冻 → CANCELLED（跨片收款方失败、需 Cancel 释放）。</p>
 */
@Getter
public enum TransferLogStatus {

    TRYING("TRYING", "受理中/协调中"),
    CONFIRMING("CONFIRMING", "确认阶段（网络不确定，可能部分完成，待下期恢复/对账）"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败（未发生资金预留）"),
    CANCELLED("CANCELLED", "已取消（曾预留已释放）");

    private final String code;
    private final String desc;

    TransferLogStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
