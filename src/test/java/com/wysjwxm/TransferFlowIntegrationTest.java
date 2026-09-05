package com.wysjwxm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 行内转账集成测试（H2 内存库）。
 *
 * 覆盖用例契约：正常划转 / 余额不足(回滚，双方不变) / 账户不存在 / 金额非法 / 自己转自己。
 * 每个用例结束后回滚，互不影响（类级 @Transactional）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransferFlowIntegrationTest {

    /** 种子账户：1 号、2 号各 1000.00（见 data.sql）。 */
    private static final long USER_A = 1L;
    private static final long USER_B = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 正常划转_双方余额正确变动且返回业务单号() throws Exception {
        ResultBody ok = transfer(USER_A, USER_B, "100.00", null);

        assertThat(ok.code()).isEqualTo(0);
        assertThat(ok.data().get("txnNo").asText()).isNotBlank();

        assertThat(balanceOf(USER_A)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(USER_B)).isEqualByComparingTo("1100.00");
    }

    @Test
    void 余额不足_转账失败且双方余额不变() throws Exception {
        ResultBody failed = transfer(USER_A, USER_B, "99999.00", null);

        assertThat(failed.code()).isEqualTo(1002); // INSUFFICIENT_BALANCE
        assertThat(balanceOf(USER_A)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(USER_B)).isEqualByComparingTo("1000.00");
    }

    @Test
    void 收款账户不存在_转账失败且付款方余额不变() throws Exception {
        ResultBody failed = transfer(USER_A, 999L, "50.00", null);

        assertThat(failed.code()).isEqualTo(1001); // ACCOUNT_NOT_FOUND
        assertThat(balanceOf(USER_A)).isEqualByComparingTo("1000.00");
    }

    @Test
    void 金额非法_转账失败() throws Exception {
        ResultBody zero = transfer(USER_A, USER_B, "0", null);
        assertThat(zero.code()).isEqualTo(1003); // INVALID_AMOUNT

        ResultBody negative = transfer(USER_A, USER_B, "-10.00", null);
        assertThat(negative.code()).isEqualTo(1003);

        ResultBody malformed = transfer(USER_A, USER_B, "abc", null);
        assertThat(malformed.code()).isEqualTo(1005); // INVALID_REQUEST（解析失败）
    }

    @Test
    void 自己转自己_转账失败() throws Exception {
        ResultBody failed = transfer(USER_A, USER_A, "100.00", null);
        assertThat(failed.code()).isEqualTo(1004); // SAME_ACCOUNT
    }

    @Test
    void 反向转账_收款方向不一致也正确() throws Exception {
        // B -> A，验证升序加锁下正反方向都成立
        ResultBody ok = transfer(USER_B, USER_A, "200.00", null);
        assertThat(ok.code()).isEqualTo(0);
        assertThat(balanceOf(USER_A)).isEqualByComparingTo("1200.00");
        assertThat(balanceOf(USER_B)).isEqualByComparingTo("800.00");
    }

    // ---- helpers ----

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

    private BigDecimal balanceOf(long userId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/accounts/" + userId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertThat(root.get("code").asInt()).isEqualTo(0);
        return new BigDecimal(root.get("data").get("balance").asText());
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
