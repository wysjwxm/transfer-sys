-- 节点 1 种子数据：拥有 userId % 2 == 1 的账户（user 1、3 各 1000.00）
-- 幂等：重复启动不会重复插入
INSERT INTO account (user_id, balance)
SELECT 1, 1000.00 WHERE NOT EXISTS (SELECT 1 FROM account WHERE user_id = 1);

INSERT INTO account (user_id, balance)
SELECT 3, 1000.00 WHERE NOT EXISTS (SELECT 1 FROM account WHERE user_id = 3);
