# Proposal

## Why

11 项能力全部交付后，项目的短板已经不在"能不能做"，而在"看起来做没做完"：管理员看板只有两个数字（`AdminDashboard.vue` 全文 31 行），团长端两个页面是裸 Element Plus 表格（`LeaderVerify.vue` 仅 21 行，输入框旁一个按钮，核销后无任何回执）。`docs/implementation-inventory.md` 里 FR-7-3（看板扩展）、FR-10-3（团长端界面）、FR-10-4（管理端界面）长期挂在 ⬜，而对应的设计稿（`docs/ui-design/design-spec.md` §6.3 核销台、§6.4 数据看板）和 6 个公共组件早已就绪——缺的只是把设计落到代码。

本变更把这三块一次性补齐：用一个接口的字段扩展把看板从"两个数字"变成"一屏运营概况"，并给团长端补上他每天真正需要的两个东西——开完团之后的经营视图，和核销时的确认感。

## What Changes

- **扩展 `GET /api/admin/dashboard`**：在原有 `todayOrders` / `todaySuccessGroups` 两个字段之外，增加今日成交额、注册用户数、进行中团购数、订单状态分布、近 7 日订单数与成交额趋势。字段名保持兼容，纯增量，仍是同一个接口。
- **新增 `GET /api/group-buys/mine/summary`**：团长本人团购的经营概览——进行中的团数、待取货订单数、已核销订单数、累计成交额。
- **新增 `GET /api/orders/verify-history`**：团长本人最近核销的订单（按核销时间倒序，最多 10 条），供核销台做"我刚做了几单"的确认与对账。
- **`AdminDashboard.vue` 重构**：KPI 卡行（复用 `StatCard`）+ 手绘 SVG 趋势折线 + 订单状态分布条形 + 数据口径说明。不引入图表库。
- **`LeaderGroups.vue` 改造**：顶部经营概览 KPI 行，列表列补「还差几人」与「核销进度」，开团表单不动。
- **`LeaderVerify.vue` 重构为核销台专注模式**：大号等宽取货码输入 + ≥46px 主操作按钮 + 安全边界说明（仅限本团 / 防重复）+ 最近核销记录列表。
- **明确不做**：移动端响应式（FR-10-5）、`AdminProducts.vue` 商品管理改造、引入 ECharts 等图表库、看板自动刷新与 WebSocket 推送、报表导出、看板数据的时间区间自选（固定"今日 + 近 7 日"）。

## Capabilities

### New Capabilities

<!-- 无。三块改动都能落进既有能力边界，不新建 UI 型能力（"团长控制台"会同时蚕食 group-buying 与 pickup-verification，属 catch-all）。 -->

### Modified Capabilities

- `admin-dashboard`: 数据契约从两项 KPI 扩展为完整看板（新增今日成交额及其口径、注册用户数、进行中团购数、订单状态分布、近 7 日趋势），并规定无数据时的展示
- `group-buying`: 「团长管理自己的团购」在团购列表之外增加经营概览指标（进行中团数、待取货 / 已核销订单数、累计成交额），且严格限定团长只能看到本人名下的数据
- `pickup-verification`: 新增「最近核销记录」——团长可查看本人近期核销的订单，仅限本人名下团购，按核销时间倒序，不泄露买家身份明细

## Impact

- **后端**：`AdminController`（返回结构由 `Map<String, Long>` 改为看板 VO）、`GroupBuyController` / `GroupBuyService`、`OrderController` / `OrderService`；`OrderMapper`、`GroupBuyMapper`、`UserMapper` 增加只读聚合查询；新增 2～3 个 DTO/VO
- **数据库**：无表结构变更、无迁移脚本，全部为只读聚合查询
- **前端**：`AdminDashboard.vue` / `LeaderGroups.vue` / `LeaderVerify.vue` 三个页面重构；`api/admin.js`、`api/groupBuy.js`、`api/order.js` 各增一个方法；`components/StatCard.vue`、`constants/status.js`、`styles/tokens.css` 直接复用；**不新增任何依赖**
- **测试**：现有 52 个用例须全绿；新增看板字段口径与两个新接口的集成用例（含跨团长越权断言）
- **文档**：`docs/implementation-inventory.md`（FR-7-3 / FR-10-3 / FR-10-4 转 ✅，接口数 33 → 35）、`README.md` 接口一览、`docs/verification-report.md` 追加本轮实测
