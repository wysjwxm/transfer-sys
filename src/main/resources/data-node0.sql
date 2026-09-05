-- 节点 0 种子数据：拥有 userId % 2 == 0 的账户（user 2、4 各 1000.00）
-- 幂等：重复启动不会重复插入
INSERT INTO account (user_id, balance)
SELECT 2, 1000.00 WHERE NOT EXISTS (SELECT 1 FROM account WHERE user_id = 2);

INSERT INTO account (user_id, balance)
SELECT 4, 1000.00 WHERE NOT EXISTS (SELECT 1 FROM account WHERE user_id = 4);
