# Design

## Context

看板与团长端三页的现状：

- `AdminController.dashboard()` 返回 `Result<Map<String, Long>>`，只有 `todayOrders` 与 `todaySuccessGroups` 两个数字，两条 SQL 都是 `OrderMapper.countTodayOrders` / `GroupBuyMapper.countTodaySuccess` 上的手写 `@Select`。
- `GET /api/group-buys/mine` 由 `GroupBuyService.listByLeader` 直接返回 `GroupBuy` 实体列表，**不含金额信息**——订单金额在 `order.amount`，团长端页面无法在前端算出成交额。
- `POST /api/orders/verify` 返回 `Result<Void>`，核销成功后前端拿不到任何订单信息，也就无法就地展示"最近核销记录"。
- 前端 `AdminDashboard.vue`（31 行）与 `LeaderVerify.vue`（21 行）是裸 Element Plus；`styles/tokens.css`、`components/StatCard.vue`、`constants/status.js` 已就绪可直接复用。`StatCard` 已支持 `trend` 与 5 种 `tone`。
- 设计稿 `docs/ui-design/design-spec.md` §6.3 要求核销台"去掉一切干扰 + 下方列最近核销记录"，§6.4 要求看板"图表用轻量手绘 SVG，不引入重型图表库"。

约束：项目主包已达 1.27MB，前端不新增依赖；`order` / `group_buy` / `user` 表均有 `deleted TINYINT NOT NULL DEFAULT 0` 且实体带 `@TableLogic`，但**手写 `@Select` 不会被 MyBatis-Plus 自动追加逻辑删除条件**。

## Goals / Non-Goals

**Goals:**

- 一个看板接口一次返回首屏全部数据，不靠前端并发打多个请求拼装
- 金额口径在看板、团长概览两处保持一致，且能被集成测试锁死
- 三个页面的改造只消费数据，不在前端复制状态文案或配色规则
- 不改动数据库表结构

**Non-Goals:**

- 不做看板数据的时间区间自选（固定"今日 + 近 7 日"）、不做导出、不做自动刷新或 WebSocket 推送
- 不做移动端响应式（FR-10-5）与 `AdminProducts.vue` 改造
- 不新增 `verified_at` 之类的字段，核销时间沿用现有列

## Decisions

### D1 看板响应改为专用 VO，字段名保持向后兼容

新建 `dto/AdminDashboardVO`：保留 `todayOrders`、`todaySuccessGroups` 两个既有字段名，新增 `todayGmv`（`BigDecimal`）、`totalUsers`、`ongoingGroupBuys`、`orderStatusDistribution`（`List<StatusCount>`）、`trend7d`（`List<TrendPoint{date, orders, gmv}>`）。

替代方案：拆成 `/dashboard/kpi`、`/dashboard/trend`、`/dashboard/distribution` 三个接口——否决，首屏三次往返换来的只是响应类型简单，而看板本身就是一个界面的一块数据。另一方案是继续用 `Map<String, Object>` 塞嵌套结构——否决，丢失类型且序列化行为不可控。

### D2 金额与计数口径落在 SQL，且所有手写查询显式带 `deleted = 0`

今日成交额：`SELECT COALESCE(SUM(amount), 0) FROM \`order\` WHERE DATE(created_at) = CURDATE() AND status NOT IN ('PENDING_PAYMENT','CANCELLED','REFUNDED') AND deleted = 0`。

- `COALESCE` 是规格里"今日无成交展示 0 而非空白"的直接落点：`SUM` 无行时返回 `NULL`，`Long`/`BigDecimal` 字段会拿到 `null` 并在前端渲染成空白。
- 排除的三个状态正好对应"钱还没进来"（待支付）、"单子没了"（已取消）、"钱退回去了"（已退款），管理员退款兜底与超时关单因此都不会虚增成交额。
- 团长累计成交额用同一状态集合，只把时间维度换成全量并加 `group_buy.leader_id = #{leaderId}` 约束。

同时把既有 `countTodayOrders` / `countTodaySuccess` 补上 `deleted = 0`，使全部只读聚合口径一致。

替代方案：口径写在 Service 层 Java 里过滤——否决，聚合下沉到 Java 要把整表订单读进内存，且无法用一条 SQL 断言。

### D3 近 7 日趋势：SQL 只出有数据的日，Java 侧补日期骨架

SQL 为 `GROUP BY DATE(created_at)`，返回稀疏结果；服务侧生成"当日往前 6 天"共 7 个日期，缺失日填 `orders=0, gmv=0`。

替代方案：MySQL 8 递归 CTE 造日历表或引入数字辅助表——否决，为 7 个点写一段递归 CTE 的可读性远差于 6 行 Java 循环，且需要改 `db/schema.sql`；本决策不触碰表结构。

### D4 状态文案与配色只有前端一处来源

后端 `StatusCount` 只返回 `{status: "PENDING_PICKUP", count: 3}` 这样的枚举键，不返回中文文案与颜色。中文文案与 tone 的唯一映射源仍是 `frontend/src/constants/status.js`，前端按 `ORDER_STATUS` 的定义顺序渲染分布条。

替代方案：后端直接返回中文标签——否决，`constants/status.js` 已是既有约定的唯一映射源，出现第二处会让团购/订单/核销记录三个页面各自漂。

### D5 团长经营概览走独立接口，不在前端做 N+1 聚合

新增 `GET /api/group-buys/mine/summary`，返回 `LeaderSummaryVO{ongoingGroups, pendingPickupOrders, completedOrders, totalGmv}`。

替代方案一：前端拿 `/group-buys/mine` 再逐团调 `/group-buys/{id}/orders` 聚合——否决，N+1 请求且每次开页打十几条查询。替代方案二：把概览塞进 `/group-buys/mine` 的响应外层——否决，列表接口的返回类型会从 `List` 变成对象，前端与既有断言都得跟着改。

### D6 最近核销记录：新查询 + 固定 10 条 + 不回传买家身份

新增 `GET /api/orders/verify-history`，`@PreAuthorize("hasRole('LEADER')")`，SQL join `group_buy` 取标题：本人名下、`status = 'COMPLETED'`、`deleted = 0`，`ORDER BY updated_at DESC LIMIT 10`，返回 `VerifyRecordVO{orderNo, groupBuyTitle, quantity, amount, verifiedAt}`——**不含 `userId` 与买家信息**，这是规格里"不泄露买家身份明细"的落点。条数固定 10，不接客户端 `limit` 参数，避免变成任意大的数据出口。

核销时间取 `updated_at` 的依据：`db/schema.sql:76` 该列为 `ON UPDATE CURRENT_TIMESTAMP`，核销是订单进入 `COMPLETED` 时的最后一次状态写入；而 `OrderService.refundOrderByAdmin`（约 OrderService.java:169）的状态守卫明确排除 `COMPLETED`，评价只写 `review` 表不碰订单行，因此已核销订单的 `updated_at` 不会再被改动。

替代方案：`verify` 接口返回被核销的订单，前端自己攒列表——否决，刷新页面即丢，且跨会话无法回答"我这上午核了几单"。

### D7 趋势图手绘 SVG，状态分布用 CSS 百分比条

趋势：`<svg viewBox="0 0 W H" preserveAspectRatio="none">` + `polyline` 折线 + 半透明面积填充 + 7 个数据点圆点，日期与数值用页面文字节点标注在图下方，不做 SVG 内文字缩放（避免窄屏字挤在一起）。

状态分布：不用 SVG，直接用 `div` 宽度百分比画水平条——它是"占比 + 计数"的一维信息，条形足够，且能天然继承 `tokens.css` 的配色与圆角。

替代方案：引入 ECharts / vue-echarts——否决，设计稿 §6.4 明确要求不引重型图表库，且主包已 1.27MB。

### D8 核销台交互沿用既有错误通路

`utils/request.js` 的响应拦截器已经对 `code != 200` 弹 `ElMessage.error` 并 `reject`，因此"失败保留输入"在现状下天然成立（`await` 抛错后不会执行清空）。本轮只需补：成功后 `focus()` 回输入框、成功后刷新最近核销记录、以及把成功/失败从一次性 toast 升级成页面内的结果条。不改动拦截器。

## Risks / Trade-offs

- **手写 SQL 漏 `deleted = 0`** → 所有新增只读聚合一律显式带上；补一条集成用例：插入 `deleted=1` 的已支付订单，断言它不进成交额、不进状态分布、不进核销记录
- **改既有两条计数 SQL 带来的回归面** → 改动是纯收窄（测试库与种子数据的 `deleted` 恒为 0，语义等价），靠 `mvn test` 全量回归证明；若回归出现失败，说明命中了真实脏数据，按缺陷处理而非回退口径
- **趋势与"今日"口径依赖 `CURDATE()`，跨零点执行用例会抖动** → 用例不依赖 `db/seed.sql` 的相对时间，而是显式插入指定 `created_at` 的订单再断言，日期在测试内自造
- **`updated_at` 作为核销时间是"当前无可变路径"的推论而非结构保证** → 若将来给已核销订单增加任何回写（如补发、修改金额），核销时间会漂移。记录在案；届时新增 `verified_at` 列才是正解，本轮明确不改表
- **看板一次返回全部聚合，数据量长大后 `GROUP BY` 变慢** → 毕设数据规模（30 笔订单）无风险；聚合已收在单接口单批查询里，未来加缓存或拆接口的改动面局限在 `AdminController` 与看板页
- **三页同时改，前端回归面铺开** → 后端接口先行并单独实测，前端三页各自独立提交，任一遍翻车不影响另两遍；`vite build` 作为每页的最低门槛

## Migration Plan

无表结构变更、无数据迁移。合入顺序：后端（VO + Mapper 查询 + 3 个接口）先行并跑 `mvn test`，前端三页随后。回滚只需 revert 对应提交——看板 VO 保留了 `todayOrders` / `todaySuccessGroups` 字段名，旧版 `AdminDashboard.vue` 在新后端下仍能正常渲染；新增的两个接口无人消费时直接失效，不影响既有链路。

## Open Questions

- `StatCard` 已支持右上角 `trend` 徽标，但"今日成交额环比昨日"需要再查一次昨日同日窗口。本变更先不填 `trend`，等确认这个环比口径要不要进入看板长期契约再决定。
- 团长概览的"待取货订单数"是否应与"已成团但团长还没核销的团"合并成一个待办入口。本变更只出数字，跳转与筛选留待后续。
