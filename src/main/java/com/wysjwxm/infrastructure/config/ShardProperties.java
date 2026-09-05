package com.wysjwxm.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 节点身份与对端地址（配置前缀 {@code transfer}，值见 application-node0.yml / application-node1.yml）。
 *
 * <p>双实例分库版契约：{@code shardOf(userId) = userId % 2}，本节点只拥有满足 {@code userId % 2 == nodeId}
 * 的账户。跨片转账时，发起方（转出方所在）节点作为协调者，通过 {@code peerUrl} 把收款方分支打到对端
 * 节点的 {@code /internal/tcc/*}。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "transfer")
public class ShardProperties {

    /** 本节点分片号：持有 userId % 2 == nodeId 的账户。 */
    private int nodeId;

    /** 对端（另一分片）节点根地址，如 http://localhost:8081。 */
    private String peerUrl;
}
