-- 幂等种子数据：账户 1、2 初始各 1000.00（重复启动不会重复插入）
INSERT INTO account (user_id, balance)
SELECT 1, 1000.00 WHERE NOT EXISTS (SELECT 1 FROM account WHERE user_id = 1);

INSERT INTO account (user_id, balance)
SELECT 2, 1000.00 WHERE NOT EXISTS (SELECT 1 FROM account WHERE user_id = 2);
