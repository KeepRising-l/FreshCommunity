-- 已有数据库升级：先执行以下查询检查历史重复取货码。
-- SELECT pickup_code, COUNT(*) FROM `order` WHERE pickup_code IS NOT NULL
-- GROUP BY pickup_code HAVING COUNT(*) > 1;
-- 如有重复，须先核对并妥善处理受影响订单，再执行此迁移；不要直接重建数据库。
-- 唯一索引允许多条 NULL（尚未生成取货码的订单）。
ALTER TABLE `order` ADD UNIQUE INDEX uk_order_pickup_code (pickup_code);
