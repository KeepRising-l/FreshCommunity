# Proposal

## Why

AI 导购的匹配提醒存在一个实现清单里挂了很久的 P2 缺口（`docs/implementation-inventory.md` §4）：**并发场景下的漏提醒窗口**与**通知不可跳转**。

1. **漏提醒窗口**：当前匹配只发生在两条同步路径上——订阅创建时扫进行中团购（`SubscriptionService.matchAgainstOngoing`）、开团时扫全部订阅（`GroupBuyService.createGroupBuy` → `matchNewGroupBuy`）。两条路径各自读库判断"对方存在与否"，若"创建订阅"与"开团"几乎同时发生，双方都可能在对方提交前完成扫描 → 该命中对永远没有提醒，且事后没有任何补偿机制。
2. **"只提醒一次"没有落地保障**：规格（ai-subscription「开团匹配提醒」）写明"同一订阅对同一团购 SHALL 只提醒一次"，但实现里没有任何去重记录——同一条匹配路径重复执行就会重复发提醒，仅靠调用方恰好只调一次的巧合。
3. **通知不可跳转**：订阅命中提醒只有纯文案，`notification` 表只有 `ref_order_id`，用户看到"你关注的商品开团了"还得自己去团购广场翻。

## What Changes

- **新增匹配记录表 `subscription_match`**：`(subscription_id, group_buy_id)` 唯一键作为"只提醒一次"的数据库级闸门。所有匹配路径（订阅时立即匹配 / 开团触发 / 补偿扫描）统一走 `INSERT IGNORE` 抢占，插入成功才发提醒——并发下天然只成功一次。
- **补偿扫描**：新增定时任务（60 秒周期，与既有任务一致），以 SQL 直连方式找出"符合条件但尚无匹配记录"的「订阅 × 进行中团购」组合，补记录并发提醒。即使并发创建双方都漏了，60 秒内也会被补偿，彻底关闭漏提醒窗口。
- **通知可跳转**：`notification` 表新增 `ref_group_buy_id`；订阅命中提醒携带团购引用；前端消息中心点击消息直达目标——订阅命中 → 团购详情，订单类事件（成团/解散/关单/核销/退款）→ 订单详情。
- **明确不做**：WebSocket/长轮询实时推送（仍是 60 秒内补偿的准实时口径）、通知模板化、订阅暂停/恢复、匹配记录的过期清理（毕设数据量下无必要）。

## Capabilities

### Modified Capabilities

- `ai-subscription`: 「开团匹配提醒」增加匹配记录闸门与补偿扫描的可靠性口径；新增「提醒可跳转」（命中提醒携带团购引用，消息中心点击直达团购详情）
- `notifications`: 新增「消息跳转」——携带引用的消息（订单类事件 → 订单详情，订阅命中 → 团购详情）在消息中心可点击直达

## Impact

- **数据库**：新增 `subscription_match` 表（唯一键去重）；`notification` 表加 `ref_group_buy_id BIGINT NULL`；`db/schema.sql` 同步 + 新增 `db/migrate-2026-10-25-subscription-match.sql`
- **后端**：新增 `entity/SubscriptionMatch` + `SubscriptionMatchMapper`（INSERT IGNORE）；`SubscriptionService` 匹配路径统一走"抢记录 → 发提醒"；`NotificationService.notify` 增加 refGroupBuyId 重载；`Notification` 实体补字段；`ScheduledJobs` 新增补偿任务
- **前端**：`MyNotifications.vue` 消息点击跳转（按类型带引用路由）；`api/notification.js` 不变（数据结构纯增量）
- **测试**：新增去重（双路径只发一条）、补偿扫描（绕过同步路径的订阅被补齐且不重复）、提醒携带团购引用等用例；现有 `SubscriptionApiTest` / `NotificationApiTest` 须全绿
- **文档**：README 核心机制一句话、`docs/implementation-inventory.md`（P2 转 ✅）、`docs/verification-report.md` 追加实测
