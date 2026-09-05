package com.wysjwxm.crossshard;

import com.wysjwxm.application.PeerCallException;
import com.wysjwxm.application.TccPeer;
import com.wysjwxm.application.TransferAppService;
import com.wysjwxm.application.TransferCommand;
import com.wysjwxm.application.TransferResult;
import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.domain.repository.AccountRepository;
import com.wysjwxm.domain.repository.TransferLogRepository;
import com.wysjwxm.domain.transfer.BranchRole;
import com.wysjwxm.domain.transfer.TransferLog;
import com.wysjwxm.domain.transfer.TransferLogStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 跨分库 TCC 编排测试（节点1 视角，@MockBean 假扮对端收款节点）。
 *
 * <p>本测试只验证<b>协调者</b>（付款方所在节点）的行为——服务级验证，不动真 HTTP：
 * 付款方冻结/真扣/解冻都在节点1 自己的库，收方节点用 mock 的 TccPeer 替身。
 * 目的是把 CANCELLED / CONFIRMING 这两个最容易讲错的终态钉死成可断言的契约。
 * 真实双节点 HTTP 链路由冒烟脚本覆盖（两个实例各连一库互调）。</p>
 *
 * <p>类级 @Transactional 每用例回滚，账户回到种子。mock 只在各用例内 stub，互不污染。</p>
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:transfer_test_cross;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class CrossShardOrchestrationTest {

    /** 转出方：1号在节点1（本上下文）。 */
    private static final long PAYER = 1L;
    /** 收款方：2号偶数属节点0（对端），本节点不持有。 */
    private static final long PAYEE_ON_PEER = 2L;

    @Autowired
    private TransferAppService transferAppService;

    @Autowired
    private TransferLogRepository transferLogRepository;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private TccPeer tccPeer;

    @Test
    void 跨片成功_付款方真扣_流水SUCCESS() {
        when(tccPeer.tryBranch(anyString(), eq(BranchRole.PAYEE), anyLong(), any(BigDecimal.class))).thenReturn(0);
        when(tccPeer.confirmBranch(anyString(), eq(BranchRole.PAYEE), anyLong(), any(BigDecimal.class))).thenReturn(0);

        TransferResult result = transferAppService.transfer(command(PAYER, PAYEE_ON_PEER, "100.00", "x-ok"));

        assertThat(result.getTxnNo()).isNotBlank();
        assertThat(balanceOf(PAYER)).isEqualByComparingTo("900.00");
        assertThat(frozenOf(PAYER)).isEqualByComparingTo("0.00");
        assertLogTerminal("x-ok", TransferLogStatus.SUCCESS);

        // 对端只以 PAYEE 角色被调（本地 PAYER 侧不走端口）
        verify(tccPeer).tryBranch(anyString(), eq(BranchRole.PAYEE), anyLong(), any(BigDecimal.class));
        verify(tccPeer).confirmBranch(anyString(), eq(BranchRole.PAYEE), anyLong(), any(BigDecimal.class));
    }

    @Test
    void 跨片收款方不存在_付款方解冻_终态CANCELLED() {
        when(tccPeer.tryBranch(anyString(), eq(BranchRole.PAYEE), anyLong(), any(BigDecimal.class)))
                .thenReturn(ErrorCode.ACCOUNT_NOT_FOUND.getCode());   // 对端明确拒绝

        assertThatThrownBy(() -> transferAppService.transfer(command(PAYER, PAYEE_ON_PEER, "100.00", "x-missing")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));

        // 曾冻结(100)又解冻归还 → 余额回到种子、无残留冻结，终态 CANCELLED
        assertThat(balanceOf(PAYER)).isEqualByComparingTo("1000.00");
        assertThat(frozenOf(PAYER)).isEqualByComparingTo("0.00");
        assertLogTerminal("x-missing", TransferLogStatus.CANCELLED);

        verify(tccPeer, never()).confirmBranch(anyString(), any(BranchRole.class), anyLong(), any(BigDecimal.class));
    }

    @Test
    void 跨片付款方余额不足_冻结前失败_终态FAILED_不调对端() {
        // 本地 Try 冻结就在抛 INSUFFICIENT_BALANCE → 未发生任何预留，终态 FAILED
        assertThatThrownBy(() -> transferAppService.transfer(command(PAYER, PAYEE_ON_PEER, "99999.00", "x-over")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));

        assertThat(balanceOf(PAYER)).isEqualByComparingTo("1000.00");
        assertThat(frozenOf(PAYER)).isEqualByComparingTo("0.00");
        assertLogTerminal("x-over", TransferLogStatus.FAILED);

        verifyNoInteractions(tccPeer);
    }

    @Test
    void 跨片确认阶段对端网络失败_终态CONFIRMING() {
        when(tccPeer.tryBranch(anyString(), eq(BranchRole.PAYEE), anyLong(), any(BigDecimal.class))).thenReturn(0);
        when(tccPeer.confirmBranch(anyString(), eq(BranchRole.PAYEE), anyLong(), any(BigDecimal.class)))
                .thenThrow(new PeerCallException("对端超时"));      // 结果未知，不能回滚也不能当成功

        assertThatThrownBy(() -> transferAppService.transfer(command(PAYER, PAYEE_ON_PEER, "150.00", "x-uncertain")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.SYSTEM_ERROR));

        // 本地付款方已真扣(150)，但收款方状态未知 → CONFIRMING 遗留待下期恢复/对账
        assertThat(balanceOf(PAYER)).isEqualByComparingTo("850.00");
        assertThat(frozenOf(PAYER)).isEqualByComparingTo("0.00");
        assertLogTerminal("x-uncertain", TransferLogStatus.CONFIRMING);
    }

    // ---- helpers ----

    private TransferCommand command(long from, long to, String amount, String requestNo) {
        return new TransferCommand(from, to, new BigDecimal(amount), requestNo);
    }

    private void assertLogTerminal(String requestNo, TransferLogStatus expected) {
        TransferLog log = transferLogRepository.findByRequestNo(requestNo);
        assertThat(log).isNotNull();
        assertThat(log.getStatus()).isEqualTo(expected);
    }

    private BigDecimal balanceOf(long userId) {
        return accountRepository.findById(userId).getBalance();
    }

    private BigDecimal frozenOf(long userId) {
        return accountRepository.findById(userId).getFrozenAmount();
    }
}
