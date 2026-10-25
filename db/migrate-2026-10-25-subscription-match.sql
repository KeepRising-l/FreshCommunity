-- ============================================================================
-- 增量迁移：2026-10-25 变更「subscription-match-reliability」
--
-- 适用场景：已有按旧版 schema.sql 建好的 freshcommunity 库，不想重建时执行本脚本。
-- 新库直接跑 db/schema.sql 即可，无需本脚本。
--
-- 注意：列/表已存在时重复执行会报 Duplicate column / Table already exists，
-- 属预期行为（说明已迁移过）。
-- ============================================================================

USE freshcommunity;

-- 订阅×团购匹配记录：唯一键作为"只提醒一次"的数据库级闸门
CREATE TABLE IF NOT EXISTS subscription_match (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    group_buy_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sub_group (subscription_id, group_buy_id),
    INDEX idx_group (group_buy_id)
);

-- 订阅命中提醒携带团购引用（消息中心点击直达团购详情）
ALTER TABLE notification ADD COLUMN ref_group_buy_id BIGINT COMMENT '关联团购（订阅命中提醒）' AFTER ref_order_id;
