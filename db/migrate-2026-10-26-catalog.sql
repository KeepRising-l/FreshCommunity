-- ============================================================================
-- 增量迁移：2026-10-26 变更「catalog-reviews-admin」
--
-- 适用场景：已有库升级（新库直接跑 db/schema.sql）。列/表已存在时报错属预期。
-- ============================================================================

USE freshcommunity;

CREATE TABLE IF NOT EXISTS product_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_product (product_id)
);

ALTER TABLE review ADD COLUMN leader_reply VARCHAR(500) COMMENT '团长回复（一层，可修改）' AFTER content;
ALTER TABLE review ADD COLUMN leader_replied_at DATETIME COMMENT '回复时间' AFTER leader_reply;
