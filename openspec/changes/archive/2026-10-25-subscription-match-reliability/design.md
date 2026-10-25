# Design

## Context

- 现有匹配是纯内存判断：`matchAgainstOngoing`（订阅创建）与 `matchNewGroupBuy`（开团）各自 `selectList` 后逐对发提醒，无任何去重记录；两条路径都挂在各自业务事务里。
- `notification` 表只有 `ref_order_id`；实体 `Notification` 直接序列化给前端。
- 定时任务基础设施已就绪（`ScheduledJobs` 三个 60s 任务 + `scheduling.initial-delay-ms` 测试隔离配置）。
- 项目约束：无 WebSocket、不引入 MQ；毕设数据量（订阅/团购都是小表），不需要消息队列或分布式锁。

## Goals / Non-Goals

**Goals:**

- 「同一订阅 × 同一团购」的提醒由数据库唯一键保证至多一次，与调用次数/并发无关
- 并发"建订阅 × 开团"的漏提醒窗口由周期补偿扫描关闭（最坏延迟 ≈ 扫描周期 60 秒）
- 订阅命中提醒可从消息中心一键直达团购详情
- 不改变既有提醒的文案、口碑摘要与事务原子性（提醒仍与匹配记录同事务写入）

**Non-Goals:**

- 不做实时推送、通知模板、已读回执
- 不清理历史匹配记录（取消订阅后匹配记录保留：换新订阅视为新的意向，允许再次提醒）

## Decisions

### D1 用 `INSERT IGNORE` + 唯一键做"抢记录"，抢到才发提醒

`subscription_match(subscription_id, group_buy_id)` 带 `UNIQUE KEY uk_sub_group`。所有匹配路径对每个命中对执行 `INSERT IGNORE`：

- 影响行数 = 1 → 本次匹配首次命中，发提醒；
- 影响行数 = 0 → 已有记录（他人/先前路径已发），跳过。

替代方案：应用层 `selectCount` 后再 insert——两步之间仍有竞态，等于没修；`INSERT ... ON DUPLICATE KEY` 语义相同但写法更绕。`INSERT IGNORE` 一条语句原子完成判定与占位，是 MySQL 下最简的正确做法。

提醒与匹配记录在**同一事务**内写入：事务回滚时记录与提醒一起消失，下次扫描重新补偿，不会出现"有记录没提醒"的死区。

### D2 补偿扫描用一条 SQL 直连找出全部漏配对

```sql
SELECT us.id AS subId, us.user_id AS userId, gb.id AS groupId, ...
FROM user_subscription us
JOIN group_buy gb   ON gb.status='ONGOING' AND gb.deleted=0 AND gb.deadline > NOW()
JOIN group_buy_item gi ON gi.group_buy_id=gb.id AND gi.deleted=0
JOIN product p      ON p.id=gi.product_id AND p.deleted=0
LEFT JOIN subscription_match sm ON sm.subscription_id=us.id AND sm.group_buy_id=gb.id AND sm.deleted=0
WHERE us.deleted=0
  AND p.name LIKE CONCAT('%', us.keyword, '%')
  AND (us.max_price IS NULL OR gi.price_snapshot <= us.max_price)
  AND sm.id IS NULL
```

匹配规则（关键词子串 + 价格上限）与内存版 `matches()` 完全一致地落在 SQL，扫描本身与逐条抢记录/发提醒复用同一套 `tryMatchAndNotify`。任务周期 60 秒，与既有任务对齐；测试 profile 的 `initial-delay` 已隔离，测试中直接调 service 方法。

注意 SQL 与内存实现的一致性风险：`matches()` 是 `productName.contains(keyword)`，SQL 是 `LIKE '%kw%'`，语义相同；转义差异（`%`/`_` 在关键词里）对"生鲜商品名"数据域无实际影响，README 已有"关键词来自解析服务归一映射"的前提。

### D3 通知跳转字段放在 notification 表而非前端解析文案

`notification` 新增 `ref_group_buy_id BIGINT NULL`。订阅命中提醒同时不填 `ref_order_id`（它是团购级事件，不是订单事件）。前端按类型路由：

- `SUBSCRIPTION_MATCH` + `refGroupBuyId` → `/user/groups/{id}`
- 订单五类事件 + `refOrderId` → `/user/orders/{id}`

替代方案：从文案里正则解析——脆弱且不可能可靠。加一列是常规做法。

### D4 取消订阅不清理匹配记录

`user_subscription` 逻辑删除后 `selectList`/JOIN 都不再命中，不会再触发匹配。保留 `subscription_match` 历史行的意义：用户若再次为新商品建订阅，是新 `subscription_id`，不冲突；同一订阅 id 恢复（产品上没有此功能）也不会重复提醒。无需清理任务。

### D5 补偿扫描的幂等与量级

扫描结果经唯一键闸门逐条抢记录，天然幂等（重复跑只是多一次 INSERT IGNORE 空转）。毕设规模下订阅×进行中团购是两位数组合，一条 JOIN 查询 + 少量插入可忽略耗时；不引入批处理框架。

## Risks / Trade-offs

- **60 秒延迟**：补偿是准实时而非实时。对"开团提醒"场景可接受（用户不会盯着秒表等提醒），README/规格口径明确"约 1 分钟内补齐"。
- **INSERT IGNORE 会吞非唯一键冲突**（如数据类型截断警告）：表就三列且都是受控值，实际风险可忽略；影响行数判断不受影响。
- **SQL 匹配与内存匹配两份实现**：集成测试用同一批数据同时断言两条路径行为一致（订阅路径 / 开团路径 / 补偿路径对同一对只发一条），一致性被测试锁死。
