package com.wysjwxm.infrastructure.peer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wysjwxm.application.PeerCallException;
import com.wysjwxm.application.TccPeer;
import com.wysjwxm.domain.transfer.BranchRole;
import com.wysjwxm.infrastructure.config.ShardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link TccPeer} 的 HTTP 实现：把 TCC 分支三阶段转发到对端节点的 /internal/tcc/*。
 *
 * <p>这是基础设施层（依赖应用层声明的端口）——跨节点交互是"外部系统"，
 * 通过 RestTemplate 完成；超时/连不上这类传输层失败收敛为 {@link PeerCallException}，
 * 业务失败原样透传对端返回的错误码（HTTP 恒 200，成败看 body.code，与 {@code Result} 约定一致）。</p>
 */
@Component
public class HttpTccPeer implements TccPeer {

    private static final Logger log = LoggerFactory.getLogger(HttpTccPeer.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String peerUrl;

    public HttpTccPeer(ShardProperties shardProperties) {
        this.peerUrl = shardProperties.getPeerUrl();

        // 节点间调用走短超时：Try 收款方失败要在秒级内判出来，才能尽快解冻付款方
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public int tryBranch(String txnNo, BranchRole role, Long userId, BigDecimal amount) {
        return post("try", txnNo, role, userId, amount);
    }

    @Override
    public int confirmBranch(String txnNo, BranchRole role, Long userId, BigDecimal amount) {
        return post("confirm", txnNo, role, userId, amount);
    }

    @Override
    public int cancelBranch(String txnNo, BranchRole role, Long userId, BigDecimal amount) {
        return post("cancel", txnNo, role, userId, amount);
    }

    private int post(String phase, String txnNo, BranchRole role, Long userId, BigDecimal amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("txnNo", txnNo);
        body.put("role", role.name());
        body.put("userId", userId);
        body.put("amount", amount.toPlainString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = peerUrl + "/internal/tcc/" + phase;
        log.debug("TCC {} -> {} body={}", phase, url, body);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new PeerCallException("对端返回非 2xx: " + response.getStatusCode() + " @ " + url);
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode code = root.path("code");
            if (!code.isInt()) {
                throw new PeerCallException("对端响应畸形(缺 code): " + response.getBody() + " @ " + url);
            }
            int resultCode = code.asInt();
            if (resultCode != 0) {
                log.warn("TCC {} 被对端拒绝 code={} msg={} txnNo={}", phase, resultCode,
                        root.path("message").asText(), txnNo);
            }
            return resultCode;
        } catch (RestClientException e) {
            throw new PeerCallException("对端不可达/超时: " + url, e);
        } catch (java.io.IOException e) {
            throw new PeerCallException("对端响应解析失败: " + url, e);
        }
    }
}
