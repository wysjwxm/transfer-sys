package com.wysjwxm.application;

import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.domain.repository.TransferLogRepository;
import com.wysjwxm.domain.transfer.TransferLog;
import com.wysjwxm.domain.transfer.TransferLogStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * transfer_log（协调记录）的唯一写入口，负责"受理落行 + 终态推进"。
 *
 * <p>为什么每个方法都是独立小事务：协调者（{@link TransferAppService}）本身不开事务，
 * 主账务事务（同片 {@link LocalTransferRunner} / 跨片各阶段）失败回滚后，
 * TRYING 行仍要在<b>另一个已提交的小事务</b>里被改成 FAILED / CANCELLED——失败也要留痕（审计）。
 * 若与主事务同包，回滚会把这行状态一并回退，就看不到终态了。</p>
 */
@Service
public class TransferLogRecorder {

    private static final String CURRENCY_CNY = "CNY";
    private static final DateTimeFormatter TXN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TransferLogRepository transferLogRepository;

    public TransferLogRecorder(TransferLogRepository transferLogRepository) {
        this.transferLogRepository = transferLogRepository;
    }

    /**
     * 受理：落一行 TRYING（含 request_no 幂等判重）。
     *
     * <p>request_no 非空时先查重：已有同号 → 回原 txn_no（replayed=true），调用方不重复动账。
     * 并发下两个同号同时来都查不到 → 双双 insert，靠唯一约束兜底：捕获冲突后回查原单号，
     * 仍然当幂等命中。返回 {@link Start}，业务是否新受理看 {@link Start#isReplayed()}。</p>
     */
    @Transactional
    public Start recordStarted(Long fromUserId, Long toUserId, BigDecimal amount, String requestNo) {
        if (requestNo != null && !requestNo.trim().isEmpty()) {
            TransferLog existed = transferLogRepository.findByRequestNo(requestNo);
            if (existed != null) {
                return new Start(existed.getTxnNo(), true);
            }
        }

        String txnNo = generateTxnNo();
        TransferLog log = new TransferLog(txnNo, requestNo, fromUserId, toUserId, amount, CURRENCY_CNY);
        try {
            transferLogRepository.insert(log);
        } catch (DuplicateKeyException e) {
            // 并发同号：唯一约束兜底。回查一次，查到即幂等命中；查不到（txn_no 撞号等罕见情况）再抛
            TransferLog existed = (requestNo == null) ? null : transferLogRepository.findByRequestNo(requestNo);
            if (existed != null) {
                return new Start(existed.getTxnNo(), true);
            }
            throw e;
        }
        return new Start(txnNo, false);
    }

    /** 终态：成功。 */
    @Transactional
    public void markSuccess(String txnNo) {
        transferLogRepository.updateStatus(txnNo, TransferLogStatus.SUCCESS, null);
    }

    /** 终态：失败（未发生资金预留）。remark 记错误码含义。 */
    @Transactional
    public void markFailed(String txnNo, ErrorCode reason) {
        transferLogRepository.updateStatus(txnNo, TransferLogStatus.FAILED, reason.getMessage());
    }

    /** 终态：已取消（曾预留已释放）。remark 记释放原因（如对端错误）。 */
    @Transactional
    public void markCancelled(String txnNo, String remark) {
        transferLogRepository.updateStatus(txnNo, TransferLogStatus.CANCELLED, truncate(remark));
    }

    /** 遗留态：确认阶段资金状态不确定（网络断点），留给下期恢复/对账。 */
    @Transactional
    public void markConfirming(String txnNo, String remark) {
        transferLogRepository.updateStatus(txnNo, TransferLogStatus.CONFIRMING, truncate(remark));
    }

    private static String truncate(String remark) {
        if (remark != null && remark.length() > 200) {
            return remark.substring(0, 200);
        }
        return remark;
    }

    /** 业务单号：时间戳 + 随机后缀，保证单节点内唯一；撞号概率可忽略（仍有 DB 唯一约束兜底）。 */
    private String generateTxnNo() {
        String time = LocalDateTime.now().format(TXN_TIME);
        int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "T" + time + suffix;
    }

    /** recordStarted 的结果：新受理的单号 / 幂等命中的原单号。 */
    @Getter
    @AllArgsConstructor
    public static class Start {

        private final String txnNo;
        private final boolean replayed;
    }
}
