package com.wysjwxm.sameshard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wysjwxm.application.TccParticipantService;
import com.wysjwxm.domain.account.Account;
import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.domain.repository.AccountRepository;
import com.wysjwxm.domain.repository.TransferBranchRepository;
import com.wysjwxm.domain.repository.TransferLogRepository;
import com.wysjwxm.domain.transfer.BranchRole;
import com.wysjwxm.domain.transfer.BranchStatus;
import com.wysjwxm.domain.transfer.TransferBranch;
import com.wysjwxm.domain.transfer.TransferLog;
import com.wysjwxm.domain.transfer.TransferLogStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 节点1(单上下文, 持有奇数账户 1、3)上的同分库转账流程测试。
 *
 * <p>同片转账强一致、不经过 TccPeer；一切 HTTP 失败路径最终态都应是无冻结的 FAILED。
 * 类级 @Transactional：每用例结束后整体回滚，账户余额回到种子、transfer_log 清空，互不影响。
 * TccParticipantService 的幂等/空回滚/悬挂守卫单独直连验证（见方法 8、9）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SameShardFlowIntegrationTest {

    /** 节点1 种子账户：1、3 号各 1000.00（data-node1.sql）。 */
    private static final long USER_A = 1L;
    private static final long USER_B = 3L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransferLogRepository transferLogRepository;

    @Autowired
    private TransferBranchRepository branchRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TccParticipantService tccParticipantService;

    @Test
    void 同片成功_双方账务与流水SUCCESS() throws Exception {
        ResultBody ok = transfer(USER_A, USER_B, "100.00", null);

        assertThat(ok.code()).isEqualTo(0);
        assertThat(ok.data().get("txnNo").asText()).isNotBlank();

        assertThat(balanceOf(USER_A)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(USER_B)).isEqualByComparingTo("1100.00");
        assertThat(frozenOf(USER_A)).isEqualByComparingTo("0.00");

        TransferLog log = transferLogRepository.findByTxnNo(ok.data().get("txnNo").asText());
        assertThat(log.getStatus()).isEqualTo(TransferLogStatus.SUCCESS);
    }

    @Test
    void 同片余额不足_终态FAILED且双方不变() throws Exception {
        ResultBody failed = transfer(USER_A, USER_B, "99999.00", "req-insufficient");

        assertThat(failed.code()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE.getCode());
        assertThat(balanceOf(USER_A)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(USER_B)).isEqualByComparingTo("1000.00");
        assertLogTerminal("req-insufficient", TransferLogStatus.FAILED);
    }

    @Test
    void 同片收款账户不存在_终态FAILED() throws Exception {
        // 9 号奇数属节点1 但未开户 → 同片收款不存在
        ResultBody failed = transfer(USER_A, 9L, "50.00", "req-no-account");

        assertThat(failed.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND.getCode());
        assertThat(balanceOf(USER_A)).isEqualByComparingTo("1000.00");
        assertLogTerminal("req-no-account", TransferLogStatus.FAILED);
    }

    @Test
    void 自己转自己_终态FAILED() throws Exception {
        ResultBody failed = transfer(USER_A, USER_A, "100.00", "req-self");

        assertThat(failed.code()).isEqualTo(ErrorCode.SAME_ACCOUNT.getCode());
        assertThat(balanceOf(USER_A)).isEqualByComparingTo("1000.00");
        assertLogTerminal("req-self", TransferLogStatus.FAILED);
    }

    @Test
    void 金额非法_零和负数为FAILED_乱码为INVALID_REQUEST不落行() throws Exception {
        ResultBody zero = transfer(USER_A, USER_B, "0", "req-zero");
        assertThat(zero.code()).isEqualTo(ErrorCode.INVALID_AMOUNT.getCode());
        assertLogTerminal("req-zero", TransferLogStatus.FAILED);

        ResultBody negative = transfer(USER_A, USER_B, "-10.00", "req-negative");
        assertThat(negative.code()).isEqualTo(ErrorCode.INVALID_AMOUNT.getCode());
        assertLogTerminal("req-negative", TransferLogStatus.FAILED);

        // 乱码连解析都过不了，属于"接口层就挡下"，不落行
        ResultBody malformed = transfer(USER_A, USER_B, "abc", "req-malformed");
        assertThat(malformed.code()).isEqualTo(ErrorCode.INVALID_REQUEST.getCode());
        assertThat(transferLogRepository.findByRequestNo("req-malformed")).isNull();
    }

    @Test
    void request_no幂等_同号回原单号且只动一次账() throws Exception {
        ResultBody first = transfer(USER_A, USER_B, "30.00", "req-dedup");
        ResultBody replay = transfer(USER_A, USER_B, "30.00", "req-dedup");

        assertThat(first.code()).isEqualTo(0);
        assertThat(replay.code()).isEqualTo(0);
        assertThat(replay.data().get("txnNo").asText()).isEqualTo(first.data().get("txnNo").asText());
        // 只执行一次：扣 30 而不是 60
        assertThat(balanceOf(USER_A)).isEqualByComparingTo("970.00");
        assertThat(balanceOf(USER_B)).isEqualByComparingTo("1030.00");
    }

    @Test
    void 打错节点_转出与查询都报WRONG_NODE() throws Exception {
        // 偶数账户属节点0，打到节点1 应被归属守卫拦下（1006），不落行
        ResultBody transferWrongNode = transfer(2L, 4L, "100.00", "req-wrong");
        assertThat(transferWrongNode.code()).isEqualTo(ErrorCode.WRONG_NODE.getCode());
        assertThat(transferLogRepository.findByRequestNo("req-wrong")).isNull();

        ResultBody queryWrongNode = getAccount(2L);
        assertThat(queryWrongNode.code()).isEqualTo(ErrorCode.WRONG_NODE.getCode());

        // 同片不存在账户（奇数但未开户）在正确节点查询 → 才是 NOT_FOUND
        ResultBody queryMissing = getAccount(9L);
        assertThat(queryMissing.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND.getCode());
    }

    @Test
    void TCC参与者_收款方为空Try且Confirm幂等() {
        // 收款方(3号)：Try 只登记不冻结（入账不会失败）；Confirm 才入账；重复 Confirm no-op
        tccParticipantService.tryBranch("B-payee-1", USER_B, BranchRole.PAYEE, new BigDecimal("50.00"));

        assertThat(balanceOf(USER_B)).isEqualByComparingTo("1000.00");
        assertThat(frozenOf(USER_B)).isEqualByComparingTo("0.00");
        assertThat(branchStatusOf("B-payee-1")).isEqualTo(BranchStatus.TRYED);

        tccParticipantService.confirmBranch("B-payee-1", USER_B, BranchRole.PAYEE, new BigDecimal("50.00"));
        tccParticipantService.confirmBranch("B-payee-1", USER_B, BranchRole.PAYEE, new BigDecimal("50.00"));

        assertThat(balanceOf(USER_B)).isEqualByComparingTo("1050.00");
        assertThat(branchStatusOf("B-payee-1")).isEqualTo(BranchStatus.CONFIRMED);
    }

    @Test
    void TCC参与者_付款方Cancel幂等_空回滚与悬挂守卫() {
        // 付款方(1号)：Try 真冻结 → Cancel 解冻归还；重复 Cancel no-op
        tccParticipantService.tryBranch("B-cancel-1", USER_A, BranchRole.PAYER, new BigDecimal("40.00"));
        assertThat(balanceOf(USER_A)).isEqualByComparingTo("960.00");
        assertThat(frozenOf(USER_A)).isEqualByComparingTo("40.00");

        tccParticipantService.cancelBranch("B-cancel-1", USER_A, BranchRole.PAYER, new BigDecimal("40.00"));
        tccParticipantService.cancelBranch("B-cancel-1", USER_A, BranchRole.PAYER, new BigDecimal("40.00"));

        assertThat(balanceOf(USER_A)).isEqualByComparingTo("1000.00");
        assertThat(frozenOf(USER_A)).isEqualByComparingTo("0.00");
        assertThat(branchStatusOf("B-cancel-1")).isEqualTo(BranchStatus.CANCELLED);

        // 空回滚：对从未 Try 的单 Cancel → 只插 CANCELLED 占位，不碰账
        tccParticipantService.cancelBranch("B-empty-1", USER_B, BranchRole.PAYEE, new BigDecimal("10.00"));
        assertThat(branchStatusOf("B-empty-1")).isEqualTo(BranchStatus.CANCELLED);
        assertThat(balanceOf(USER_B)).isEqualByComparingTo("1000.00");

        // 悬挂守卫：占位已 CANCELLED，迟到的 Try 必须拒绝，不能冻出一笔没人认领的钱
        assertThatThrownBy(() -> tccParticipantService.tryBranch("B-empty-1", USER_B, BranchRole.PAYEE, new BigDecimal("10.00")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThat(branchStatusOf("B-empty-1")).isEqualTo(BranchStatus.CANCELLED);
        assertThat(balanceOf(USER_B)).isEqualByComparingTo("1000.00");

        // 已 CANCELLED 的分支也不许 Confirm 入账（防重复入账）
        assertThatThrownBy(() -> tccParticipantService.confirmBranch("B-empty-1", USER_B, BranchRole.PAYEE, new BigDecimal("10.00")))
                .isInstanceOf(BizException.class);
    }

    // ---- helpers ----

    private void assertLogTerminal(String requestNo, TransferLogStatus expected) {
        TransferLog log = transferLogRepository.findByRequestNo(requestNo);
        assertThat(log).isNotNull();
        assertThat(log.getStatus()).isEqualTo(expected);
        assertThat(log.getRemark()).isNotBlank();   // 终态必留原因
    }

    private BranchStatus branchStatusOf(String txnNo) {
        TransferBranch branch = branchRepository.findByTxnNo(txnNo);
        assertThat(branch).isNotNull();
        return branch.getStatus();
    }

    private ResultBody transfer(long from, long to, String amount, String requestNo) throws Exception {
        String body = String.format(
                "{\"fromUserId\":%d,\"toUserId\":%d,\"amount\":\"%s\"%s}",
                from, to, amount, requestNo == null ? "" : ",\"requestNo\":\"" + requestNo + "\"");
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return new ResultBody(objectMapper.readTree(mvcResult.getResponse().getContentAsString()));
    }

    private ResultBody getAccount(long userId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/accounts/" + userId))
                .andExpect(status().isOk())
                .andReturn();
        return new ResultBody(objectMapper.readTree(mvcResult.getResponse().getContentAsString()));
    }

    private BigDecimal balanceOf(long userId) {
        return account(userId).getBalance();
    }

    private BigDecimal frozenOf(long userId) {
        return account(userId).getFrozenAmount();
    }

    private Account account(long userId) {
        Account account = accountRepository.findById(userId);
        assertThat(account).isNotNull();
        return account;
    }

    /** 简化断言用的返回体：code 与 data 节点。 */
    private static class ResultBody {
        private final int code;
        private final JsonNode data;

        ResultBody(JsonNode node) {
            this.code = node.get("code").asInt();
            this.data = node.get("data");
        }

        int code() {
            return code;
        }

        JsonNode data() {
            return data;
        }
    }
}
