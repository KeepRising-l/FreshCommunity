USE freshcommunity;

-- 演示种子数据（可选），运行后可即席演示
-- 注意：请先在 application-dev.yml 中配置正确的数据库账号密码

-- 清理旧演示数据
DELETE FROM `order` WHERE order_no LIKE 'DEMO%';
DELETE FROM group_buy_item WHERE group_buy_id IN (SELECT id FROM group_buy WHERE title LIKE 'DEMO%');
DELETE FROM group_buy WHERE title LIKE 'DEMO%';

-- 创建一场进行中的演示团购
INSERT INTO group_buy (leader_id, title, target_count, deadline, status, member_count)
SELECT id, 'DEMO-红颜草莓团', 3, DATE_ADD(NOW(), INTERVAL 2 HOUR), 'ONGOING', 0
FROM user WHERE username='leader1';

INSERT INTO group_buy_item (group_buy_id, product_id, price_snapshot)
SELECT g.id, p.id, p.price
FROM group_buy g, product p
WHERE g.title='DEMO-红颜草莓团' AND p.name='红颜草莓';
