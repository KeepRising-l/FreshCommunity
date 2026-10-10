CREATE DATABASE IF NOT EXISTS freshcommunity
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE freshcommunity;

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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    image_url VARCHAR(255),
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

-- 初始化测试账号（三个账号密码统一为：123456）
INSERT INTO user (username, password, role) VALUES
('admin', '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'ADMIN'),
('leader1', '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'LEADER'),
('user1', '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER');

UPDATE user SET pickup_point_name='幸福小区便利店', pickup_point_address='幸福小区东门 101 号' WHERE username='leader1';

INSERT INTO product (name, price, stock, image_url, status) VALUES
('红颜草莓', 29.90, 100, '', 1),
('烟台红富士', 19.90, 200, '', 1),
('新鲜菠菜', 4.50, 150, '', 1);
