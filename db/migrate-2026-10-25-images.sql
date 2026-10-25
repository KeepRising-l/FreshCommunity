-- ============================================================================
-- 增量迁移：2026-10-25 变更「real-images-avatars」
--
-- 适用场景：已有按旧版 schema.sql 建好的 freshcommunity 库，不想重建时执行本脚本。
-- 新库直接跑 db/schema.sql + db/seed.sql 即可，无需本脚本。
--
-- 注意：列已存在时重复执行会报 Duplicate column name，属预期行为（说明已迁移过）。
-- 商品/提货点的真实图片素材随仓库走（frontend/public/images/），
-- 重导 db/seed.sql 即可获得全部图片数据。
-- ============================================================================

USE freshcommunity;

ALTER TABLE user ADD COLUMN avatar_url VARCHAR(255) COMMENT '头像（/uploads/ 或 /images/ 路径）' AFTER pickup_point_address;
ALTER TABLE user ADD COLUMN pickup_point_image_url VARCHAR(255) COMMENT '团长提货点照片' AFTER avatar_url;
