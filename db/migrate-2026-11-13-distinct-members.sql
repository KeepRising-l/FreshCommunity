-- 旧库升级：成团人数从已支付订单数改为已支付的不同用户数。
-- 停止应用和定时任务并备份后执行；脚本仅校准进行中的团购，不删除订单。
-- 成团或已解散的历史记录无法可靠还原当时退款前的参与人数，保留原值并按需人工核对。
-- 执行前可先查看受影响的进行中团购：
-- SELECT g.id, g.member_count AS old_count, COUNT(DISTINCT o.user_id) AS user_count
-- FROM group_buy g LEFT JOIN `order` o ON o.group_buy_id = g.id
--   AND o.status = 'PENDING_GROUP' AND o.deleted = 0
-- WHERE g.status = 'ONGOING' AND g.deleted = 0
-- GROUP BY g.id, g.member_count HAVING old_count <> user_count;
UPDATE group_buy g
SET g.member_count = (
    SELECT COUNT(DISTINCT o.user_id)
    FROM `order` o
    WHERE o.group_buy_id = g.id AND o.status = 'PENDING_GROUP' AND o.deleted = 0
)
WHERE g.status = 'ONGOING' AND g.deleted = 0;
