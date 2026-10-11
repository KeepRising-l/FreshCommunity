# Design

## Context

现状约束（详见 proposal.md - Why）：

- 已有 5 张表：`user`、`product`、`group_buy`、`group_buy_item`、`order`；MyBatis-Plus + 逻辑删除（`deleted`）；统一返回 `{code,msg,data}`；JWT 无状态鉴权，控制器用 `@RequestAttribute("userId")` 取当前用户。
- 订单状态机：`PENDING_PAYMENT → PENDING_GROUP → SUCCESS/PENDING_PICKUP → COMPLETED`，终态 `CANCELLED`/`REFUNDED`。评价资格可从"订单是否 `COMPLETED`"直接推导。
- 需要挂通知的事件点分散在 3 处：`OrderService`（支付成团、核销、管理员退款）、`ScheduledJobs.closePendingPaymentOrders`（超时关单）、`GroupBuyService.handleExpiredGroups`（解散退款）。
- 这些事件点都运行在 `@Transactional` 方法内。

## Goals / Non-Goals

**Goals:**

- 新增 `review`、`notification` 两张表，**不改动既有表结构与状态机语义**
- 评价资格由既有订单状态推导，不引入新状态、不新增 order 列
- 通知在既有事件点"旁挂"生成，与状态变更同事务，保证原子性
- 接口风格、鉴权方式、返回包装沿用现状

**Non-Goals:**

- 不做评价审核、敏感词过滤、举报
- 不做评价的修改与删除（提交后只读）
- 不做团长回复评价
- 不做邮件/短信/App 推送等外部通道，仅站内可见
- 不做 WebSocket 实时推送，未读数由页面加载/轮询获取
- 不做消息分类开关、免打扰设置

## Decisions

**1. 评价资格 = 订单状态为 `COMPLETED` 且该订单尚无评价**

- 理由：不改 order 表、不新增状态；重复评价由 `review.order_id` 唯一索引兜底，并发提交时 `DuplicateKeyException` 会被全局异常处理器转成 400。
- 备选：在 `order` 加 `reviewed` 标记列 —— 需要改既有表且与唯一索引职责重复，否决。

**2. `review` 表冗余存储被评价对象 ID**

```sql
review(id, order_id UNIQUE, user_id, product_id, leader_id,
       product_score TINYINT, leader_score TINYINT, content VARCHAR(500),
       created_at, deleted)
```

- 理由：评分汇总可直接按 `product_id` / `leader_id` 聚合，无需 join `group_buy` 反查团长。
- 备选：只存 `order_id`，汇总时 join —— 查询更复杂且每次都要回溯团购表，否决。

**3. 评分汇总走实时聚合（`AVG` + `COUNT`），不加冗余评分列**

- 理由：当前数据规模小；避免在 product/user 上维护聚合列带来的一致性风险。
- 备选：在 `product` 加 `avg_score`/`review_count` 冗余列 —— 需在每次评价写入时更新，易不一致，且商品与团长两处都要维护，否决。后续量大了再演进。

**4. 通知在既有事件点同事务写入，统一走 `NotificationService.notify(...)`**

- 理由：与状态变更原子，不会出现"订单已退款但没通知"。
- 备选：Spring 领域事件 + `@TransactionalEventListener(AFTER_COMMIT)` —— 更解耦，但引入异步时序与失败处理复杂度，MVP 阶段过重，否决。

**5. `notification` 表结构**

```sql
notification(id, user_id, type, title, content, ref_order_id,
             is_read TINYINT DEFAULT 0, created_at, deleted,
             INDEX idx_user_read (user_id, is_read))
```

- `type` 取值：`GROUP_SUCCESS` / `GROUP_FAILED` / `ORDER_TIMEOUT` / `PICKUP_DONE` / `REFUNDED`
- 未读数 = `COUNT(*) WHERE user_id = ? AND is_read = 0`
- 标记已读的 UPDATE 一律带 `AND user_id = ?` 条件，从 SQL 层面保证"消息仅本人可见"

**6. 接口与路径沿用现状**

- `/api/reviews`：`POST`（提交评价）、`GET /api/reviews/mine`（我的评价）、`GET /api/reviews/product/{id}`（商品评价列表 + 汇总）
- `/api/notifications`：`GET`（列表）、`GET /unread-count`、`POST /{id}/read`、`POST /read-all`
- 评分汇总随商品详情/团购详情一并返回，前端不再单独请求

## Risks / Trade-offs

- [通知写入拉长事务] → 通知表单条写入很轻，且与业务同事务是刻意选择，可接受
- [评分实时聚合随数据量变慢] → 当前规模无影响；预留后续加冗余列或缓存的演进空间
- [未读数非实时] → 站内信非强实时场景，页面加载时拉取即可
- [并发重复评价] → 唯一索引 + 全局 `DuplicateKeyException` 处理，返回 400 而非 500
- [评价内容可能含敏感词] → 明确列为 Non-Goal，答辩时如实说明边界

## Migration Plan

1. `db/schema.sql` 追加 `review`、`notification` 的 `CREATE TABLE`（脚本保持可重复执行）
2. 新表为纯增量，不影响既有数据与接口
3. 回滚：删除两张表 + 回退代码即可，无数据迁移负担

## Open Questions

- 评价是否匿名？暂定**不匿名**，展示用户名
- 商品详情评价列表是否分页？暂定**不分页**，展示最新若干条（实现时定具体条数）
