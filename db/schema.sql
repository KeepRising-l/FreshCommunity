CREATE DATABASE IF NOT EXISTS freshcommunity
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE freshcommunity;

DROP TABLE IF EXISTS subscription_match;
DROP TABLE IF EXISTS review_analysis;
DROP TABLE IF EXISTS user_subscription;
DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS `order`;
DROP TABLE IF EXISTS group_buy_item;
DROP TABLE IF EXISTS group_buy;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS user;

CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'USER/LEADER/ADMIN',
    pickup_point_name VARCHAR(128) COMMENT '团长提货点名称',
    pickup_point_address VARCHAR(255) COMMENT '团长提货点地址',
    avatar_url VARCHAR(255) COMMENT '头像（/uploads/ 或 /images/ 路径）',
    pickup_point_image_url VARCHAR(255) COMMENT '团长提货点照片',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT '其他' COMMENT '分类（自由文本）',
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    image_url VARCHAR(255),
    description VARCHAR(500) COMMENT '一句话介绍',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE group_buy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    leader_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    target_count INT NOT NULL COMMENT '成团人数',
    deadline DATETIME NOT NULL COMMENT '截止时间',
    status VARCHAR(20) NOT NULL DEFAULT 'ONGOING' COMMENT 'ONGOING/SUCCESS/FAIL',
    member_count INT NOT NULL DEFAULT 0 COMMENT '已支付人数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_status_deadline (status, deadline)
);

CREATE TABLE group_buy_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_buy_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    price_snapshot DECIMAL(10,2) NOT NULL COMMENT '开团时商品价格快照',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE `order` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    group_buy_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT 'PENDING_PAYMENT/PENDING_GROUP/SUCCESS/PENDING_PICKUP/COMPLETED/CANCELLED/REFUNDED',
    pickup_code VARCHAR(32) COMMENT '取货码',
    pay_time DATETIME COMMENT '支付时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_group_buy_id (group_buy_id),
    INDEX idx_pickup_code (pickup_code)
);

CREATE TABLE review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '被评价的订单',
    user_id BIGINT NOT NULL COMMENT '评价人',
    product_id BIGINT NOT NULL COMMENT '被评商品',
    leader_id BIGINT NOT NULL COMMENT '被评团长',
    product_score TINYINT NOT NULL COMMENT '商品评分 1-5',
    leader_score TINYINT NOT NULL COMMENT '团长评分 1-5',
    content VARCHAR(500) COMMENT '文字评论',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_order_id (order_id),
    INDEX idx_product_id (product_id),
    INDEX idx_leader_id (leader_id)
);

CREATE TABLE notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '接收人',
    type VARCHAR(32) NOT NULL COMMENT 'GROUP_SUCCESS/GROUP_FAILED/ORDER_TIMEOUT/PICKUP_DONE/REFUNDED/SUBSCRIPTION_MATCH',
    title VARCHAR(128) NOT NULL,
    content VARCHAR(500),
    ref_order_id BIGINT COMMENT '关联订单（订单类事件）',
    ref_group_buy_id BIGINT COMMENT '关联团购（订阅命中提醒）',
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_user_read (user_id, is_read)
);

-- AI 导购：用户自然语言需求订阅（匹配命中的提醒经 notification 表发送）
CREATE TABLE user_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    keyword VARCHAR(64) NOT NULL COMMENT '归一后的商品关键词',
    max_price DECIMAL(10,2) COMMENT '价格上限（可为空，用户未提价格时）',
    source_text VARCHAR(255) COMMENT '用户原始输入',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_user (user_id)
);

-- AI 导购：订阅×团购匹配记录。(subscription_id, group_buy_id) 唯一键是
-- "同一订阅对同一团购只提醒一次"的数据库级闸门：所有匹配路径以 INSERT IGNORE
-- 抢占记录，插入成功（首占）才发提醒；并发创建漏配的由定时补偿扫描补齐。
CREATE TABLE subscription_match (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    group_buy_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sub_group (subscription_id, group_buy_id),
    INDEX idx_group (group_buy_id)
);

-- AI 导购：评价洞察（每条评价一行，定时批量分析生成）
CREATE TABLE review_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL COMMENT '1:1 关联评价',
    product_id BIGINT NOT NULL COMMENT '冗余商品 id，供按商品聚合标签',
    sentiment VARCHAR(16) NOT NULL COMMENT 'POSITIVE/NEGATIVE/NEUTRAL',
    tags VARCHAR(255) COMMENT '逗号分隔关键词，如"新鲜,实惠"',
    analyzed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_review_id (review_id),
    INDEX idx_product_id (product_id)
);

-- 初始化测试账号（三个账号密码统一为：123456）
INSERT INTO user (username, password, role) VALUES
('admin', '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'ADMIN'),
('leader1', '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'LEADER'),
('user1', '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER');

UPDATE user SET pickup_point_name='幸福小区便利店', pickup_point_address='幸福小区东门 101 号' WHERE username='leader1';

INSERT INTO product (name, category, price, stock, image_url, description, status) VALUES
('红颜草莓', '水果', 29.90, 100, '', '奶香浓郁的红颜草莓，现摘现发', 1),
('烟台红富士', '水果', 19.90, 200, '', '山东烟台红富士，脆甜多汁', 1),
('新鲜菠菜', '蔬菜', 4.50, 150, '', '基地直采嫩菠菜，当天现摘', 1);
