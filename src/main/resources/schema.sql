-- 行内转账 · 建表脚本
-- 只使用 H2(MODE=MySQL) 与真 MySQL 均支持的通用子集；
-- 字段说明与领域含义见 domain 包，SQL 内不写 COMMENT（两边兼容）。
-- 注意：账户表假设"一人一账户"，主键即 user_id（MVP 口径，见 TODO M0.7）。

CREATE TABLE IF NOT EXISTS account (
    user_id     BIGINT         NOT NULL,
    balance     DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
);

-- 转账流水表（transfer_log）：整笔一行、成功才落库（路线甲，TODO M0.3）
-- 它是转账的业务记录而非纯日志，后续承载状态流转/查询/冲正锚点。
-- txn_no     = 全局唯一业务单号（回执/冲正/对账锚点，TODO M0.5）
-- request_no = 请求方幂等键占位：本期只建唯一约束，防重逻辑下期做（TODO M0.9）
-- status     = 预留列，MVP 恒为 SUCCESS（TODO M0.8）
CREATE TABLE IF NOT EXISTS transfer_log (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    txn_no       VARCHAR(40)    NOT NULL,
    request_no   VARCHAR(64)    DEFAULT NULL,
    from_user_id BIGINT         NOT NULL,
    to_user_id   BIGINT         NOT NULL,
    amount       DECIMAL(19, 2) NOT NULL,
    currency     VARCHAR(8)     NOT NULL DEFAULT 'CNY',
    status       VARCHAR(16)    NOT NULL DEFAULT 'SUCCESS',
    create_time  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_transfer_log_txn_no  UNIQUE (txn_no),
    CONSTRAINT uk_transfer_log_request UNIQUE (request_no)
);
