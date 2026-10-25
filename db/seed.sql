-- ============================================================================
-- 演示种子数据（丰富版）
--
-- 用法：先执行 schema.sql 建库建表，再执行本脚本。
--   mysql -uroot -p1234 --default-character-set=utf8mb4 < db/seed.sql
--
-- 特性：
--   * 可重复执行 —— 每次都会清空业务表并重建，得到完全一致的演示数据
--   * 时间为相对时间（NOW()），任何时候执行都能得到"看起来正在发生"的数据
--   * 所有账号密码统一为 123456
--
-- 覆盖的状态（方便答辩演示）：
--   商品：41 个，同一品类多品种多价格带（含 AI 导购演示专用的「红富士苹果」，
--         以及 9.9 元的「甘肃花牛苹果」让低价订阅命中有料）
--   团购：进行中（进度 0/N、1/N、2/N）、即将截止、已成团待取货、已全部核销、已解散
--   订单：待支付 / 待成团 / 已成团待取货 / 已完成 / 已取消 / 已退款
--   评价：11 条（覆盖 4 个商品、2 个团长，含一条低分；苹果 3 条用于口碑摘要演示）
--   通知：27 条（成团 / 解散 / 核销 / 超时关单），部分未读
--   订阅：0 条（演示时由 user1 现场创建）
-- ============================================================================

USE freshcommunity;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 清空业务数据
DELETE FROM review_analysis;
DELETE FROM user_subscription;
DELETE FROM review;
DELETE FROM notification;
DELETE FROM `order`;
DELETE FROM group_buy_item;
DELETE FROM group_buy;
DELETE FROM product;
DELETE FROM user;

-- ---------------------------------------------------------------------- 用户
-- 1 管理员 / 2-4 团长 / 5-16 普通用户，密码均为 123456
INSERT INTO user (id, username, password, role, pickup_point_name, pickup_point_address, pickup_point_image_url) VALUES
(1,  'admin',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'ADMIN',  NULL, NULL, NULL),
(2,  'leader1', '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'LEADER', '幸福小区便利店',   '幸福小区东门 101 号', '/images/pickup/pickup1.jpg'),
(3,  'leader2', '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'LEADER', '阳光花园快递驿站', '阳光花园 3 号楼底商', '/images/pickup/pickup2.jpg'),
(4,  'leader3', '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'LEADER', '翠湖天地物业前台', '翠湖天地南门物业中心', '/images/pickup/pickup3.jpg'),
(5,  'user1',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(6,  'user2',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(7,  'user3',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(8,  'user4',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(9,  'user5',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(10, 'user6',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(11, 'user7',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(12, 'user8',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(13, 'user9',   '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(14, 'user10',  '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(15, 'user11',  '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL),
(16, 'user12',  '$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi', 'USER',   NULL, NULL, NULL);

-- ---------------------------------------------------------------------- 商品
-- price 是「零售价」。团购的「拼团价」存在 group_buy_item.price_snapshot（见下方），
-- 两者刻意拉开差距，前端才能展示「立省 X 元」。订单金额一律按 price_snapshot 计算。
INSERT INTO product (id, name, category, price, stock, image_url, description, status) VALUES
(1,  '红颜草莓',     '水果',     39.90, 100, '/images/products/p01.jpg', '奶香浓郁的红颜草莓，空运直达', 1),
(2,  '烟台红富士',   '水果',     26.90, 200, '/images/products/p02.jpg', '山东烟台红富士，脆甜多汁', 1),
(3,  '新鲜菠菜',     '蔬菜',      6.90, 150, '/images/products/p03.jpg', '基地直采嫩菠菜，当天现摘', 1),
(4,  '海南贵妃芒',   '水果',     19.90, 120, '/images/products/p04.jpg', '海南三亚贵妃芒，核薄肉厚', 1),
(5,  '赣南脐橙',     '水果',     18.90, 180, '/images/products/p05.jpg', '江西赣南脐橙，酸甜比例刚好', 1),
(6,  '新西兰奇异果', '水果',     49.90,  60, '/images/products/p06.jpg', '阳光金果奇异果，一箱 9 个装', 1),
(7,  '本地小番茄',   '蔬菜',     12.90, 160, '/images/products/p07.jpg', '沙瓤小番茄，生吃拌糖都合适', 1),
(8,  '有机胡萝卜',   '蔬菜',      7.90, 140, '/images/products/p08.jpg', '有机认证胡萝卜，脆甜多汁', 1),
(9,  '云南紫皮蒜',   '粮油调味',  9.90, 130, '/images/products/p09.jpg', '云南独头紫皮蒜，蒜香浓', 1),
(10, '山东大葱',     '蔬菜',      5.50, 220, '/images/products/p10.jpg', '章丘大葱，葱白长而甜', 1),
(11, '鲜鸡蛋 30 枚', '蛋奶',     36.80,  90, '/images/products/p11.jpg', '谷物喂养鲜鸡蛋，30 枚整箱', 1),
(12, '散养土鸡',     '肉禽蛋',   89.00,  40, '/images/products/p12.jpg', '林间散养土鸡，整只约 2.5 斤', 1),
(13, '黑猪五花肉',   '肉禽蛋',   59.90,  50, '/images/products/p13.jpg', '黑猪五花肉，肥瘦相间五层', 1),
(14, '鲜活基围虾',   '水产',     79.90,  35, '/images/products/p14.jpg', '今晨到货活虾，冰鲜直发', 1),
(15, '深海带鱼段',   '水产',     42.00,  45, '/images/products/p15.jpg', '东海带鱼中段，船冻锁鲜', 1),
(16, '有机鲜牛奶',   '蛋奶',     16.80, 110, '/images/products/p16.jpg', '每日期鲜牛奶，750ml×2 瓶', 1),
-- 17 专为 AI 导购演示准备：演示脚本用的是「10 元以内的苹果」，
-- 只有存在一个零售价够低、拼团价能压到 10 元以下的苹果，订阅才可能被命中。
(17, '红富士苹果',   '水果',     12.90, 120, '/images/products/p17.jpg', '红富士苹果，产地直发性价比之选', 1),
-- 18-41：同一品类多品种多价格带（如苹果三档：9.9 / 12.9 / 15.9），撑起检索、
-- 分类筛选与 AI 导购订阅命中（"X 元以内的苹果"可同时命中多个品种）的演示深度
(18, '阿克苏冰糖心苹果', '水果',     15.90,  80, '/images/products/p18.jpg', '阿克苏冰糖心苹果，脆甜多汁核透亮', 1),
(19, '甘肃花牛苹果', '水果',      9.90,  90, '/images/products/p19.jpg', '花牛蛇果，粉面香甜，老人孩子都爱', 1),
(20, '阳光玫瑰葡萄', '水果',     39.90,  60, '/images/products/p20.jpg', '阳光玫瑰青提，玫瑰香气无籽脆甜', 1),
(21, '巨峰葡萄',     '水果',     15.90, 100, '/images/products/p21.jpg', '经典巨峰葡萄，酸甜多汁有果香', 1),
(22, '库尔勒香梨',   '水果',     21.90,  85, '/images/products/p22.jpg', '新疆库尔勒香梨，皮薄核小汁水足', 1),
(23, '软籽石榴',     '水果',     29.90,  55, '/images/products/p23.jpg', '突尼斯软籽石榴，不用吐籽清甜爆汁', 1),
(24, '高山甜香蕉',   '水果',     12.90, 130, '/images/products/p24.jpg', '高山甜香蕉，自然熟软糯香甜', 1),
(25, '琯溪蜜柚',     '水果',     18.90,  70, '/images/products/p25.jpg', '平和琯溪蜜柚，粒粒爆汁清甜微酸', 1),
(26, '麒麟西瓜',     '水果',     25.90,  40, '/images/products/p26.jpg', '麒麟西瓜皮薄肉脆，甜到瓜皮', 1),
(27, '智利车厘子',   '水果',     89.00,  30, '/images/products/p27.jpg', '空运车厘子 J 级，一口一个停不下', 1),
(28, '沙瓤大番茄',   '蔬菜',      5.90, 140, '/images/products/p28.jpg', '沙瓤大番茄，炒蛋拌糖都合适', 1),
(29, '水果黄瓜',     '蔬菜',      6.90, 120, '/images/products/p29.jpg', '水果黄瓜脆嫩无刺，生吃正合适', 1),
(30, '黄心土豆',     '蔬菜',      3.90, 200, '/images/products/p30.jpg', '黄心土豆粉糯，炖菜烧肉都好吃', 1),
(31, '奶油生菜',     '蔬菜',      3.50, 110, '/images/products/p31.jpg', '奶油生菜嫩滑爽口，沙拉必备', 1),
(32, '紧花西兰花',   '蔬菜',      9.90,  95, '/images/products/p32.jpg', '紧花西兰花，颗颗紧实翠绿', 1),
(33, '新鲜香菇',     '蔬菜',     12.90,  80, '/images/products/p33.jpg', '鲜香菇肉厚味浓，煲汤爆炒两相宜', 1),
(34, '鸡翅中',       '肉禽蛋',   29.90,  60, '/images/products/p34.jpg', '单冻鸡翅中，可乐鸡翅专属', 1),
(35, '黄牛牛腩块',   '肉禽蛋',   49.90,  45, '/images/products/p35.jpg', '黄牛牛腩块，炖煮软烂入味', 1),
(36, '新鲜猪肋排',   '肉禽蛋',   55.90,  40, '/images/products/p36.jpg', '新鲜猪肋排，糖醋红烧都出彩', 1),
(37, '冰鲜三文鱼段', '水产',     68.00,  35, '/images/products/p37.jpg', '冰鲜三文鱼段，刺身级新鲜', 1),
(38, '生态海鲈鱼',   '水产',     25.90,  50, '/images/products/p38.jpg', '生态海鲈鱼，清蒸鲜嫩少刺', 1),
(39, '老酸奶',       '蛋奶',     19.90,  75, '/images/products/p39.jpg', '表层结皮的老酸奶，浓稠到挂勺', 1),
(40, '五常大米 5kg', '粮油调味', 39.90,  65, '/images/products/p40.jpg', '五常稻花香，米油厚润饭香扑鼻', 1),
(41, '压榨花生油 1.8L', '粮油调味', 79.90, 50, '/images/products/p41.jpg', '物理压榨花生油，炒菜更香', 1);

-- 明星商品补第二张图（图库演示）：草莓/红富士/花牛 各两张，第二张复用近邻素材
INSERT INTO product_image (product_id, image_url, sort) VALUES
(1,  '/images/products/p01.jpg', 0),
(1,  '/images/products/p18.jpg', 1),
(2,  '/images/products/p02.jpg', 0),
(2,  '/images/products/p17.jpg', 1),
(19, '/images/products/p19.jpg', 0),
(19, '/images/products/p18.jpg', 1);

-- -------------------------------------------------------------------- 团购活动
-- 1-3   今日成团（updated_at = 今天 → 计入看板"今日成团数"）
-- 4-7   进行中（其中 7 即将截止）
-- 8-9   已解散（deadline 已过，状态已定格，定时任务不会再动它）
-- 10    刚开团
-- 11    历史已成团（3 天前，全部核销，用于撑起评分数据）
INSERT INTO group_buy (id, leader_id, title, target_count, deadline, status, member_count, created_at, updated_at) VALUES
(1,  2, '红颜草莓 · 现摘现发',        3, DATE_SUB(NOW(), INTERVAL 6 HOUR),  'SUCCESS', 3, DATE_SUB(NOW(), INTERVAL 1 DAY),  NOW()),
(2,  3, '赣南脐橙 · 产地直发',        3, DATE_SUB(NOW(), INTERVAL 4 HOUR),  'SUCCESS', 3, DATE_SUB(NOW(), INTERVAL 1 DAY),  NOW()),
(3,  4, '散养土鸡 · 整只冷冻',        3, DATE_SUB(NOW(), INTERVAL 3 HOUR),  'SUCCESS', 3, DATE_SUB(NOW(), INTERVAL 2 DAY),  NOW()),
(4,  2, '本地小番茄 · 沙瓤沙甜',      5, DATE_ADD(NOW(), INTERVAL 1 DAY),   'ONGOING', 2, DATE_SUB(NOW(), INTERVAL 5 HOUR), NOW()),
(5,  3, '鲜活基围虾 · 今早到货',      3, DATE_ADD(NOW(), INTERVAL 2 DAY),   'ONGOING', 1, DATE_SUB(NOW(), INTERVAL 3 HOUR), NOW()),
(6,  4, '鲜鸡蛋 30 枚 · 土鸡蛋',     10, DATE_ADD(NOW(), INTERVAL 3 DAY),   'ONGOING', 0, DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW()),
(7,  2, '烟台红富士 · 脆甜多汁',      3, DATE_ADD(NOW(), INTERVAL 1 HOUR),  'ONGOING', 2, DATE_SUB(NOW(), INTERVAL 8 HOUR), NOW()),
(8,  3, '黑猪五花肉 · 冷鲜配送',      5, DATE_SUB(NOW(), INTERVAL 1 DAY),   'FAIL',    2, DATE_SUB(NOW(), INTERVAL 4 DAY),  DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9,  4, '深海带鱼段 · 船冻直供',      8, DATE_SUB(NOW(), INTERVAL 2 DAY),   'FAIL',    3, DATE_SUB(NOW(), INTERVAL 5 DAY),  DATE_SUB(NOW(), INTERVAL 2 DAY)),
(10, 2, '有机鲜牛奶 · 每日配送',      3, DATE_ADD(NOW(), INTERVAL 6 HOUR),  'ONGOING', 0, DATE_SUB(NOW(), INTERVAL 20 MINUTE), NOW()),
(11, 2, '新鲜菠菜 · 有机种植',        4, DATE_SUB(NOW(), INTERVAL 3 DAY),   'SUCCESS', 4, DATE_SUB(NOW(), INTERVAL 5 DAY),  DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 12    苹果历史团（2 天前成团并核销）——给 AI 导购演示提供「口碑摘要」数据源
(12, 2, '红富士苹果 · 产地直发',      3, DATE_SUB(NOW(), INTERVAL 2 DAY),   'SUCCESS', 3, DATE_SUB(NOW(), INTERVAL 3 DAY),  DATE_SUB(NOW(), INTERVAL 2 DAY));

-- price_snapshot = 该团购的拼团价，恒低于 product.price（零售价），差额即前端展示的「立省」。
-- 订单金额 = price_snapshot × quantity，见下方 order 表。
INSERT INTO group_buy_item (group_buy_id, product_id, price_snapshot) VALUES
(1,  1,  29.90),
(2,  5,  12.90),
(3,  12, 68.00),
(4,  7,   8.80),
(5,  14, 59.90),
(6,  11, 26.80),
(7,  2,  19.90),
(8,  13, 45.00),
(9,  15, 32.00),
(10, 16, 12.50),
(11, 3,   4.50),
-- 12：苹果历史团，拼团价 9.90（演示「10 元以内的苹果」订阅的条件）
(12, 17,  9.90);

-- ------------------------------------------------------------------------ 订单
-- 已支付（计入团购人数）的状态：PENDING_GROUP / SUCCESS / PENDING_PICKUP / COMPLETED
-- 不计入人数：PENDING_PAYMENT / CANCELLED / REFUNDED
INSERT INTO `order` (id, order_no, user_id, group_buy_id, product_id, quantity, amount, status, pickup_code, pay_time, created_at, updated_at) VALUES
-- 团购 1：已成团并全部核销（3 人）
(1,  'D0001', 5,  1, 1,  1, 29.90, 'COMPLETED', '100001', DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(2,  'D0002', 6,  1, 1,  2, 59.80, 'COMPLETED', '100002', DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(3,  'D0003', 7,  1, 1,  1, 29.90, 'COMPLETED', '100003', DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR)),
-- 团购 2：已成团，等待提货（3 人）
(4,  'D0004', 8,  2, 5,  1, 12.90, 'SUCCESS',   '200001', DATE_SUB(NOW(), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(5,  'D0005', 9,  2, 5,  3, 38.70, 'SUCCESS',   '200002', DATE_SUB(NOW(), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(6,  'D0006', 5,  2, 5,  1, 12.90, 'SUCCESS',   '200003', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR)),
-- 团购 3：已成团，部分已核销（3 人）
(7,  'D0007', 10, 3, 12, 1, 68.00, 'COMPLETED', '300001', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(8,  'D0008', 11, 3, 12, 1, 68.00, 'SUCCESS',   '300002', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9,  'D0009', 12, 3, 12, 1, 68.00, 'SUCCESS',   '300003', DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR)),
-- 团购 4：进行中 2/5（含一笔未支付）
(10, 'D0010', 5,  4, 7,  2, 17.60, 'PENDING_GROUP',   NULL, DATE_SUB(NOW(), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(11, 'D0011', 6,  4, 7,  1,  8.80, 'PENDING_GROUP',   NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(12, 'D0012', 7,  4, 7,  1,  8.80, 'PENDING_PAYMENT', NULL, NULL, DATE_SUB(NOW(), INTERVAL 10 MINUTE), DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
-- 团购 5：进行中 1/3
(13, 'D0013', 8,  5, 14, 1, 59.90, 'PENDING_GROUP',   NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR)),
-- 团购 7：即将截止 2/3
(14, 'D0014', 9,  7, 2,  2, 39.80, 'PENDING_GROUP',   NULL, DATE_SUB(NOW(), INTERVAL 7 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 7 HOUR)),
(15, 'D0015', 10, 7, 2,  1, 19.90, 'PENDING_GROUP',   NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR)),
-- 团购 8：已解散，订单已退款
(16, 'D0016', 11, 8, 13, 1, 45.00, 'REFUNDED', NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(17, 'D0017', 12, 8, 13, 2, 90.00, 'REFUNDED', NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 团购 9：已解散，订单已退款
(18, 'D0018', 5,  9, 15, 1, 32.00, 'REFUNDED', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(19, 'D0019', 6,  9, 15, 1, 32.00, 'REFUNDED', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(20, 'D0020', 7,  9, 15, 1, 32.00, 'REFUNDED', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
-- 团购 11：历史已成团并全部核销（4 人，3 天前）
(21, 'D0021', 8,  11, 3, 2, 9.00, 'COMPLETED', '400001', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(22, 'D0022', 9,  11, 3, 2, 9.00, 'COMPLETED', '400002', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(23, 'D0023', 10, 11, 3, 1, 4.50, 'COMPLETED', '400003', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(24, 'D0024', 11, 11, 3, 2, 9.00, 'COMPLETED', '400004', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 团购 12：苹果历史团（3 人，2 天前，全部核销）—— 供 AI 导购演示的口碑摘要
(28, 'D0028', 8,  12, 17, 1, 9.90, 'COMPLETED', '500001', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(29, 'D0029', 9,  12, 17, 2, 19.80, 'COMPLETED', '500002', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(30, 'D0030', 10, 12, 17, 1, 9.90, 'COMPLETED', '500003', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
-- 已取消 / 超时关单（体现"取消规则"与"30 分钟自动关单"）
(25, 'D0025', 5,  4, 7,  1,  8.80, 'CANCELLED', NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(26, 'D0026', 6,  5, 14, 1, 59.90, 'CANCELLED', NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(27, 'D0027', 7,  6, 11, 2, 53.60, 'CANCELLED', NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ------------------------------------------------------------------------ 评价
-- 只对 COMPLETED 订单评价，一单一评（order_id 唯一）
INSERT INTO review (order_id, user_id, product_id, leader_id, product_score, leader_score, content, created_at) VALUES
(1,  5,  1,  2, 5, 5, '草莓很新鲜，个头也大，孩子一口气吃了半盒', DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(2,  6,  1,  2, 4, 5, '味道不错，就是取货时间稍微晚了点',       DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(3,  7,  1,  2, 5, 4, '第二次买了，品质一直稳定',               DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(7,  10, 12, 4, 5, 5, '土鸡炖汤很香，团长人也热情',             DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(21, 8,  3,  2, 4, 4, '菠菜挺嫩的，就是分量一般',               DATE_SUB(NOW(), INTERVAL 3 DAY)),
(22, 9,  3,  2, 5, 5, '很新鲜，下次还买',                       DATE_SUB(NOW(), INTERVAL 3 DAY)),
(23, 10, 3,  2, 3, 4, '这次收到的有点蔫，希望改进',             DATE_SUB(NOW(), INTERVAL 3 DAY)),
(24, 11, 3,  2, 5, 5, '性价比很高，推荐',                       DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 苹果团评价：AI 导购演示中「已有 N 人评价都说…」摘要的数据来源
(28, 8,  17, 2, 5, 5, '苹果很新鲜，个头均匀，孩子很爱吃',       DATE_SUB(NOW(), INTERVAL 2 DAY)),
(29, 9,  17, 2, 4, 5, '甜度不错，价格也实惠，下次还买',         DATE_SUB(NOW(), INTERVAL 2 DAY)),
(30, 10, 17, 2, 5, 4, '性价比很高，一箱不知不觉就吃完了',       DATE_SUB(NOW(), INTERVAL 2 DAY));

-- -------------------------------------------------------------------- 站内消息
-- 未读（is_read = 0）集中在最近的成团与核销事件上，让"我的消息"有角标可演示
INSERT INTO notification (user_id, type, title, content, ref_order_id, is_read, created_at) VALUES
-- 团购 1 成团 + 核销
(5,  'GROUP_SUCCESS', '团购已成团', '你参与的「红颜草莓 · 现摘现发」已成团，请留意提货通知。取货码：100001', 1, 0, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(6,  'GROUP_SUCCESS', '团购已成团', '你参与的「红颜草莓 · 现摘现发」已成团，请留意提货通知。取货码：100002', 2, 0, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(7,  'GROUP_SUCCESS', '团购已成团', '你参与的「红颜草莓 · 现摘现发」已成团，请留意提货通知。取货码：100003', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(5,  'PICKUP_DONE',   '提货完成',   '你的订单已核销完成，感谢参与。', 1, 0, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(6,  'PICKUP_DONE',   '提货完成',   '你的订单已核销完成，感谢参与。', 2, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(7,  'PICKUP_DONE',   '提货完成',   '你的订单已核销完成，感谢参与。', 3, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
-- 团购 2 成团
(8,  'GROUP_SUCCESS', '团购已成团', '你参与的「赣南脐橙 · 产地直发」已成团，请留意提货通知。取货码：200001', 4, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9,  'GROUP_SUCCESS', '团购已成团', '你参与的「赣南脐橙 · 产地直发」已成团，请留意提货通知。取货码：200002', 5, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(5,  'GROUP_SUCCESS', '团购已成团', '你参与的「赣南脐橙 · 产地直发」已成团，请留意提货通知。取货码：200003', 6, 0, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
-- 团购 3 成团 + 核销
(10, 'GROUP_SUCCESS', '团购已成团', '你参与的「散养土鸡 · 整只冷冻」已成团，请留意提货通知。取货码：300001', 7, 0, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(11, 'GROUP_SUCCESS', '团购已成团', '你参与的「散养土鸡 · 整只冷冻」已成团，请留意提货通知。取货码：300002', 8, 0, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(12, 'GROUP_SUCCESS', '团购已成团', '你参与的「散养土鸡 · 整只冷冻」已成团，请留意提货通知。取货码：300003', 9, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(10, 'PICKUP_DONE',   '提货完成',   '你的订单已核销完成，感谢参与。', 7, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
-- 团购 11（3 天前）成团 + 核销
(8,  'GROUP_SUCCESS', '团购已成团', '你参与的「新鲜菠菜 · 有机种植」已成团，请留意提货通知。取货码：400001', 21, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9,  'GROUP_SUCCESS', '团购已成团', '你参与的「新鲜菠菜 · 有机种植」已成团，请留意提货通知。取货码：400002', 22, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(10, 'GROUP_SUCCESS', '团购已成团', '你参与的「新鲜菠菜 · 有机种植」已成团，请留意提货通知。取货码：400003', 23, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(11, 'GROUP_SUCCESS', '团购已成团', '你参与的「新鲜菠菜 · 有机种植」已成团，请留意提货通知。取货码：400004', 24, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(8,  'PICKUP_DONE',   '提货完成',   '你的订单已核销完成，感谢参与。', 21, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9,  'PICKUP_DONE',   '提货完成',   '你的订单已核销完成，感谢参与。', 22, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(10, 'PICKUP_DONE',   '提货完成',   '你的订单已核销完成，感谢参与。', 23, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(11, 'PICKUP_DONE',   '提货完成',   '你的订单已核销完成，感谢参与。', 24, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 团购 8 解散退款
(11, 'GROUP_FAILED', '团购未成团', '你参与的「黑猪五花肉 · 冷鲜配送」到点未成团，已自动解散并全额退款。', 16, 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(12, 'GROUP_FAILED', '团购未成团', '你参与的「黑猪五花肉 · 冷鲜配送」到点未成团，已自动解散并全额退款。', 17, 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 团购 9 解散退款
(5,  'GROUP_FAILED', '团购未成团', '你参与的「深海带鱼段 · 船冻直供」到点未成团，已自动解散并全额退款。', 18, 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(6,  'GROUP_FAILED', '团购未成团', '你参与的「深海带鱼段 · 船冻直供」到点未成团，已自动解散并全额退款。', 19, 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(7,  'GROUP_FAILED', '团购未成团', '你参与的「深海带鱼段 · 船冻直供」到点未成团，已自动解散并全额退款。', 20, 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
-- 超时关单
(7,  'ORDER_TIMEOUT', '订单已关闭', '你的订单超过 30 分钟未支付，已自动关闭。', 27, 1, DATE_SUB(NOW(), INTERVAL 1 DAY));
