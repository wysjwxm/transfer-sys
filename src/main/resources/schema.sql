-- 转账系统 · 建表脚本（双实例分库版）
-- 每个节点执行同一份 schema.sql，各连自己的库：node0 拥有 userId%2==0 的账户，node1 拥有奇数。
-- 只使用 H2(MODE=MySQL) 与真 MySQL 均支持的通用子集；字段含义见 domain 包，SQL 内不写 COMMENT。

-- 账户表：一人一账户（主键即 user_id）。
-- balance       = 可用余额
-- frozen_amount = 冻结金额（跨片 TCC Try 预留；同片转账恒为 0）
-- 不变式：总资产 = balance + frozen_amount
CREATE TABLE IF NOT EXISTS account (
    user_id       BIGINT         NOT NULL,
    balance       DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    frozen_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
);

-- 转账流水表（transfer_log）：整笔转账的业务记录 + 跨片 TCC 的协调记录。
-- 由发起方（转出方所在）节点写入并驱动状态；status 从受理时即流转，不再"成功才落库"。
-- txn_no     = 全局唯一业务单号（回执/冲正/对账锚点）
-- request_no = 请求方幂等键（唯一约束真正判重：同号请求回原 txn_no）
-- status     = TRYING / CONFIRMING / SUCCESS / FAILED / CANCELLED
-- remark     = 终态原因（如错误码/描述），供审计与对账
CREATE TABLE IF NOT EXISTS transfer_log (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    txn_no       VARCHAR(40)    NOT NULL,
    request_no   VARCHAR(64)    DEFAULT NULL,
    from_user_id BIGINT         NOT NULL,
    to_user_id   BIGINT         NOT NULL,
    amount       DECIMAL(19, 2) NOT NULL,
    currency     VARCHAR(8)     NOT NULL DEFAULT 'CNY',
    status       VARCHAR(16)    NOT NULL DEFAULT 'TRYING',
    remark       VARCHAR(255)   DEFAULT NULL,
    create_time  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_transfer_log_txn_no  UNIQUE (txn_no),
    CONSTRAINT uk_transfer_log_request UNIQUE (request_no)
);

-- TCC 分支控制表（每节点一张，只记"本节点账户参与"的那一侧分支）。
-- 承担 TCC 的 幂等 / 空回滚 / 悬挂：
--   - Confirm/Cancel 重复执行：按 status 判 no-op
--   - 空回滚：Cancel 时无 TRYED 行则只插一行 CANCELLED，不做账务动作
--   - 悬挂：Try 前发现已有 CANCELLED 行则放弃预留
-- role   = PAYER（付款方，Try 冻结）/ PAYEE（收款方，Try 不冻结，仅登记）
-- status = TRYED / CONFIRMED / CANCELLED
CREATE TABLE IF NOT EXISTS tcc_branch (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    txn_no      VARCHAR(40)    NOT NULL,
    user_id     BIGINT         NOT NULL,
    role        VARCHAR(8)     NOT NULL,
    amount      DECIMAL(19, 2) NOT NULL,
    status      VARCHAR(16)    NOT NULL,
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_tcc_branch_txn UNIQUE (txn_no)
);
