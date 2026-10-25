# Tasks

## 实施顺序

数据库 → 后端（记录表闸门 → 三条路径统一 → 补偿任务 → 通知字段）→ 测试全绿 → 前端跳转 → 端到端实测与归档。前端不依赖新接口（通知列表纯增量字段），可与后端测试并行。

## 1. 数据库

- [x] 1.1 `db/schema.sql`：新增 `subscription_match` 表（id / subscription_id / group_buy_id / created_at / deleted，`UNIQUE KEY uk_sub_group(subscription_id, group_buy_id)`）；`notification` 表加 `ref_group_buy_id BIGINT NULL`，验证：重建库后 `DESC subscription_match` 与 `DESC notification` 字段正确
- [x] 1.2 新建 `db/migrate-2026-10-25-subscription-match.sql` 增量脚本（CREATE TABLE IF NOT EXISTS + ADD COLUMN，列已存在时报错属预期并注释说明），验证：对已建库执行后字段齐全

## 2. 后端

- [x] 2.1 新建 `entity/SubscriptionMatch` 与 `SubscriptionMatchMapper`（`@Insert("INSERT IGNORE ...")` 返回影响行数；按订阅/按团购删除记录的辅助查询不需要，保持最小面），验证：`mvn compile` 通过
- [x] 2.2 `Notification` 实体加 `refGroupBuyId`；`NotificationService.notify` 增加带 `refGroupBuyId` 的重载（原 5 参签名保留，订单类调用不动），验证：编译通过且既有调用点无改动
- [x] 2.3 `SubscriptionService`：抽出统一入口 `tryMatchAndNotify(sub, groupBuy, item, product)`——INSERT IGNORE 抢记录，抢到才发提醒（携带 `refGroupBuyId`）；`matchAgainstOngoing` / `matchNewGroupBuy` / 补偿扫描三处全部改走该入口，验证：三条路径对同一「订阅×团购」重复执行，提醒只有一条
- [x] 2.4 `SubscriptionMatchMapper` 增加补偿扫描查询（D2 的 JOIN SQL，找出符合条件且无记录的对），`SubscriptionService.compensateMissed()` 消费之并复用 2.3 入口，返回本次补偿条数；`ScheduledJobs` 新增 60 秒任务（`initial-delay` 配置与既有任务一致），验证：`mvn test` 全绿；日志可观测补偿条数
- [x] 2.5 新建 `SubscriptionMatchReliabilityTest`：
  - [ ] 2.5.1 双路径去重：订阅命中进行中团（收到 1 条）→ 再对该团跑 `matchNewGroupBuy` → 不新增
  - [ ] 2.5.2 重复触发去重：同一团连跑两次 `matchNewGroupBuy` → 第二次 matched=0 且提醒数不变
  - [ ] 2.5.3 补偿：绕过同步路径（jdbcTemplate 直插订阅）→ `compensateMissed()` 补 1 条提醒；再跑一次 → 0 条
  - [ ] 2.5.4 跳转字段：命中提醒的 `ref_group_buy_id` 等于团购 id、`ref_order_id` 为空；`/api/notifications` 响应含 `refGroupBuyId`
  - [ ] 2.5.5 不命中不打扰：补偿扫描不产出不符合条件的对
  - 验证：全部通过且 `SubscriptionApiTest` / `NotificationApiTest` 无回归

## 3. 前端

- [x] 3.1 `MyNotifications.vue`：消息点击 → 已读之外按引用跳转（`SUBSCRIPTION_MATCH`+refGroupBuyId → 团购详情；订单五类 + refOrderId → 订单详情），无引用的消息行为不变；行尾加「查看 ›」提示，验证：浏览器实测两类跳转可达
- [x] 3.2 `npm run build` 通过，验证：退出码 0

## 4. 验证与文档

- [x] 4.1 `mvn test` 全量通过（89 存量 + 新增 ≥6），验证：输出无 FAIL/ERROR
- [x] 4.2 端到端实测：重建库 → 起后端 → 断言去重/补偿/跳转字段/前端构建，追加 `docs/verification-report.md` §九，验证：每条断言附实际响应
- [x] 4.3 同步 `README.md`（核心机制 + 测试数）与 `docs/implementation-inventory.md`（P2 转 ✅、接口/测试计数），验证：口径与实测一致
- [x] 4.4 归档本变更并 `openspec validate --specs` 通过，验证：基线含新增 Requirement
