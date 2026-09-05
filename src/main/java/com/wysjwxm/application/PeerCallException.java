package com.wysjwxm.application;

/**
 * 对端不可达 / 调用超时 / 响应畸形等"结果未知"的传输层异常（区别于业务错误码）。
 *
 * <p>协调者在跨片编排里捕获它：处于 Try 收款方阶段说明本侧资金已冻结但收方状态未知，
 * 属"发生过预留"的失败路径，终态应为 CANCELLED（执行本地 Cancel 解冻）；处于 Confirm 阶段
 * 则资金状态完全不确定，置 CONFIRMING 遗留——不能当作确定成功或确定失败。</p>
 */
public class PeerCallException extends RuntimeException {

    public PeerCallException(String message) {
        super(message);
    }

    public PeerCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
