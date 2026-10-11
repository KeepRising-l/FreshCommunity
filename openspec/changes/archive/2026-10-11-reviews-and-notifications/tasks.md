# Tasks

## 1. 数据模型

- [x] 1.1 `db/schema.sql` 新增 `review` 表（`order_id` 唯一索引、`product_id`/`leader_id` 普通索引、逻辑删除列），脚本保持可重复执行（验证：脚本重复执行不报错，唯一索引存在）
- [x] 1.2 `db/schema.sql` 新增 `notification` 表（`user_id`+`is_read` 联合索引、`type`/`ref_order_id` 列），脚本保持可重复执行（验证：脚本重复执行不报错）
- [x] 1.3 新增 `Review`、`Notification` 实体与 Mapper（验证：应用启动无报错，可 insert 与按条件 select）

## 2. 评价能力（reviews）

- [x] 2.1 评价提交接口 `POST /api/reviews`：仅 `COMPLETED` 订单可评、一单一评（唯一索引兜底）、商品与团长评分 1–5 必填、文字评论可选且 ≤500 字符（验证：非已完成订单 400、重复评价 400、评分越界 400、正常提交 200）
- [x] 2.2 我的评价查询 `GET /api/reviews/mine`（验证：只返回当前用户自己的评价）
- [x] 2.3 商品评价列表与评分汇总 `GET /api/reviews/product/{id}`：平均分保留一位小数 + 评价条数；无评价时条数为 0 且不返回误导性的 0 分（验证：造 3 条评价后平均分与条数正确；无评价时平均分为空）
- [x] 2.4 商品详情与团购详情接口附加评分汇总字段（验证：`/api/products/{id}` 与 `/api/group-buys/{id}` 返回平均分与评价条数）
- [x] 2.5 我的订单接口标记可评价状态（验证：已完成且未评价的订单返回 `reviewed=false`，已评价返回 `true`）

## 3. 站内通知（notifications）

- [x] 3.1 `NotificationService.notify(userId, type, title, content, refOrderId)` 统一入口，以及列表 / 未读数 / 标记已读 / 全部已读接口（验证：接口只返回本人消息，未读数与已读状态一致）
- [x] 3.2 接入成团与解散事件：`OrderService.tryGroupSuccess`、`GroupBuyService.handleExpiredGroups`（验证：成团后参团用户收到成团消息；解散后用户收到解散退款消息）
- [x] 3.3 接入超时关单、核销成功、管理员退款事件：`ScheduledJobs.closePendingPaymentOrders`、`OrderService.verifyPickupCode`、`OrderService.refundOrderByAdmin`（验证：三种事件各产生一条对应消息）
- [x] 3.4 权限隔离与已读操作：标记已读的 UPDATE 带 `user_id` 条件，一键全部已读（验证：操作他人消息被拒且状态不变；全部已读后未读数为 0）

## 4. 前端

- [x] 4.1 我的订单页增加"去评价"入口与评价弹窗（商品评分 + 团长评分 + 评论）（验证：已完成未评价订单显示入口，提交后显示"已评价"且入口消失）
- [x] 4.2 商品详情页展示评分汇总与评价列表（验证：页面可见平均分、评价条数与评论内容）
- [x] 4.3 新增"我的消息"页面与顶栏未读角标，支持单条/全部标记已读（验证：有未读时显示角标，标记已读后角标减少）

## 5. 集成验收

- [x] 5.1 端到端演练：下单 → 支付成团 → 团长核销 → 用户评价 → 查看商品评分汇总与消息中心（验证：全链路跑通，评分与消息正确）
- [x] 5.2 异常路径演练：解散退款通知、超时关单通知、重复评价被拒、跨用户消息隔离（验证：与 spec 场景一一对应通过）
- [x] 5.3 更新 `README.md` 接口一览与 `db/schema.sql` 说明（验证：文档与实现一致）

## Workflow follow-up

- 全部任务完成后归档变更（archive），同步主 specs。
