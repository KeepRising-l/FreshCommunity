-- ============================================================================
-- 增量迁移：2026-10-25 变更「catalog-retrieval-user-center-admin」
--
-- 适用场景：已有按旧版 schema.sql 建好的 freshcommunity 库，不想重建时执行本脚本。
-- 新库直接跑 db/schema.sql + db/seed.sql 即可，无需本脚本。
--
-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报
--   Duplicate column name 'category'，属预期行为（说明已迁移过）。
-- 存量商品的 category 会落默认值「其他」，如需完整演示分类请重导 db/seed.sql。
-- ============================================================================

USE freshcommunity;

ALTER TABLE product ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT '其他' COMMENT '分类（自由文本）' AFTER name;
ALTER TABLE product ADD COLUMN description VARCHAR(500) COMMENT '一句话介绍' AFTER image_url;
