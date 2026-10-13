# FreshCommunity 实现清单（需求 → 实现对照）

> **用途**：答辩展示「做了什么、怎么做、还差什么」；与 `docs/requirements-analysis.md` 的功能编号逐条对应。
> **状态口径**：✅ 已交付（功能可用且有验证）｜🔸 已交付 · 视觉简版｜⬜ 待实施（已规划未开发）
> **数据截至**：2026-10-13（本清单全部数字为实测统计）

---

## 0. 总览

| 维度 | 数量 | 说明 |
| --- | --- | --- |
| 功能能力（capability） | **9 个 · 全部交付** | 35 条 requirement / 57 个 scenario，2 个变更已归档进基线 |
| 后端接口 | **29 个** | 9 个 Controller，统一 `{code, msg, data}` 返回 |
| 数据库表 | **7 张** | user / product / group_buy / group_buy_item / order / review / notification |
| 前端页面 | **14 个** | 设计系统 7 个；视觉待精修 3 个；待三期改造 4 个；另有 6 个公共组件 |
| 自动化测试 | **30 个用例** | 4 个测试类，独立测试库，带事务回滚 |
| 规格实测 | **48 项断言** | `docs/verification-report.md`（对真实运行的后端发请求） |
| 待实施 | **4 个功能点** | 看板扩展、团长端界面、管理端界面、响应式移动端 |

---

## 1. 功能实现对照（按需求编号）

### FR-1 用户与权限（user-roles）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-1-1 | 自助注册（用户名唯一 / BCrypt / 团长需提货点 / 拒绝 ADMIN） | ✅ | `AuthController.register` + `RegisterRequest` + `Register.vue` | `AuthApiTest`：registerSuccess / registerDuplicateUsernameRejected / registerAdminRejected / registerLeaderRequiresPickupPoint |
| FR-1-2 | 登录与 JWT 身份识别 | ✅ | `AuthController.login` / `me` + `JwtUtil` | `AuthApiTest`：loginSuccess / loginWrongPasswordReturns401 |
| FR-1-3 | 角色权限隔离 | ✅ | `SecurityConfig` + 各 Controller `@PreAuthorize` + 归属校验 | `GroupBuyFlowApiTest`：userCannotListGroupOrders / otherLeaderCannotListGroupOrders + verification-report §三 |
| FR-1-4 | 团长提货点资料 | ✅ | `user` 表字段 + 团购详情 VO | `GroupBuyFlowApiTest.listContainsLeaderAndPickupPoint` |

### FR-2 商品管理（product-catalog）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-2-1 | 商品上架 | ✅ | `ProductController`（POST）+ `AdminProducts.vue` | verification-report §三（缺必填字段返回 400） |
| FR-2-2 | 商品编辑（改价立即生效） | ✅ | `ProductController`（PUT / DELETE） | verification-report §三（改价立即生效） |
| FR-2-3 | 商品浏览（列表 / 详情 / 搜索 / 评分汇总） | 🔸 | `ProductController`（GET）+ `ProductList.vue` / `ProductDetail.vue` | `ReviewApiTest.detailEndpointsExposeRating`；页面为功能简版（视觉未按设计稿精修） |
| FR-2-4 | 商品图片上传（≤5MB，仅管理员） | ✅ | `FileController` + `application.yml`（max-file-size: 5MB） | verification-report §五（用户 403 / 管理员 200） |

### FR-3 团购活动（group-buying）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-3-1 | 发起团购（人数 / 截止时间校验） | ✅ | `GroupBuyController.create` + `GroupBuyService.createGroupBuy` | `GroupBuyFlowApiTest.createGroupBuyRequiresLeaderRole` + verification-report §三（截止时间早于当前被拒且未落库） |
| FR-3-2 | 参团下单（仅进行中可下单） | ✅ | `OrderController.create` + `OrderService.createOrder` | `GroupBuyFlowApiTest.orderRejectedWhenStockInsufficient` + verification-report §三（关闭后下单被拒） |
| FR-3-3 | 模拟支付（收银台 / 计入人数 / 预留扩展点） | ✅ | `PayService` 接口 + `MockPayServiceImpl` | verification-report §三（支付后转 PENDING_GROUP 并计入人数） |
| FR-3-4 | 提前成团（达标即成团 / 生成取货码 / 只成功一次） | ✅ | `OrderService.tryGroupSuccess` + `GroupBuyMapper.updateStatus`（条件更新） | verification-report §三 + `NotificationApiTest.groupSuccessNotification`（成团事件） |
| FR-3-5 | 到点解散退款（定时任务） | ✅ | `ScheduledJobs.processExpiredGroupBuys` + `GroupBuyService.handleExpiredGroups`（60 秒周期） | verification-report §三（团购 FAIL、订单 REFUNDED、库存回补、截止后补单被拒） |
| FR-3-6 | 团长管理自己的团购 | ✅ | `GroupBuyController.mine` / `{id}/orders` + `LeaderGroups.vue` | `GroupBuyFlowApiTest`：leaderCanListGroupOrders / otherLeaderCannotListGroupOrders |

### FR-4 库存管理（inventory）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-4-1 | 下单原子扣减防超卖 | ✅ | `ProductMapper.deductStock`（`UPDATE ... WHERE stock >= n`） | verification-report §三（**库存=1 时 8 个并发下单仅 1 单成功**） |
| FR-4-2 | 库存不足拦截 | ✅ | 同上（受影响行数 0 即拒） | `GroupBuyFlowApiTest.orderRejectedWhenStockInsufficient` |
| FR-4-3 | 取消 / 退款回补库存 | ✅ | `ProductMapper.restoreStock` | verification-report §三（取消回补）+ `GroupBuyFlowApiTest.duplicateRefundRejected`（退款不重复回补） |

### FR-5 订单生命周期（order-lifecycle）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-5-1 | 状态机合法流转 | ✅ | `OrderService` + `OrderMapper.updateStatus`（带预期状态条件） | verification-report §三（已成团不可取消等） |
| FR-5-2 | 待成团可取消（回补库存 / 递减人数 / 模拟退款） | ✅ | `OrderService.cancelOrder` | `GroupBuyFlowApiTest.cancelOrderRollsBackMemberCount` |
| FR-5-3 | 成团后不可取消 | ✅ | `OrderService.cancelOrder` 状态守卫 | verification-report §三（已成团不可取消） |
| FR-5-4 | 待支付 30 分钟超时关单 | ✅ | `ScheduledJobs.closePendingPaymentOrders` | `GroupBuyFlowApiTest.pendingPaymentOrderAutoClosed` |
| FR-5-5 | 唯一 6 位取货码（SecureRandom + 查重） | ✅ | `OrderService.generatePickupCode` | verification-report §五（批量成团取货码互不重复） |
| FR-5-6 | 管理员退款兜底（终态守卫 / 回退人数库存） | ✅ | `OrderService.refundOrderByAdmin` | `GroupBuyFlowApiTest.duplicateRefundRejected` + verification-report §五 |

### FR-6 提货核销（pickup-verification）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-6-1 | 输码核销（订单转已完成） | ✅ | `OrderController.verify` + `OrderService.verifyPickupCode` | verification-report §三（有效码核销转 COMPLETED） |
| FR-6-2 | 防重复核销 | ✅ | 同上（状态守卫） | verification-report §三（重复核销被拒） |
| FR-6-3 | 仅限本团订单 | ✅ | 同上（归属校验） | verification-report §三 |

### FR-7 数据看板（admin-dashboard）—— 基础交付 / 扩展待实施

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-7-1 | 今日订单数 | ✅ | `AdminController.dashboard` | verification-report §三（与数据库直查结果一致） |
| FR-7-2 | 今日成团数 | ✅ | 同上 | verification-report §三 |
| FR-7-3 | 看板扩展（成交额 / 趋势 / 状态分布） | ⬜ | 设计稿预留（design-spec §6.4） | — |

### FR-8 评价体系（reviews）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-8-1 | 评价资格（已完成 + 一单一评） | ✅ | `ReviewService.submit` + `review.order_id` 唯一索引 | `ReviewApiTest`：reviewRejectedForUnfinishedOrder / reviewOnceThenRejected |
| FR-8-2 | 评价内容（双评分必填 / 文字可选） | ✅ | 同上 + `ReviewCreateRequest` 校验 | `ReviewApiTest.scoreOutOfRangeRejected` |
| FR-8-3 | 订单页评价入口 | ✅ | `UserOrders.vue` 评价弹窗 + `Order` VO 的 `reviewed` 标记 | `ReviewApiTest.orderListExposesReviewedFlag` |
| FR-8-4 | 评分汇总（实时聚合 / 无评价不误导） | ✅ | `ReviewService.productRating` / `leaderRating` | `ReviewApiTest`：ratingAggregation / noReviewMeansNoScore |
| FR-8-5 | 评价可见性与只读 | ✅ | `ReviewController`（GET）+ 商品/团购详情附带评分 | `ReviewApiTest.detailEndpointsExposeRating` |

### FR-9 站内消息（notifications）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-9-1 | 团购结果通知（成团 / 解散） | ✅ | `NotificationService.notify`（同事务旁挂）+ `MyNotifications.vue` | `NotificationApiTest`：groupSuccessNotification / refundNotification |
| FR-9-2 | 订单状态变更通知（超时 / 核销 / 退款） | ✅ | 同上（3 处事件点） | `NotificationApiTest.pickupDoneNotification` + refundNotification |
| FR-9-3 | 消息仅本人可见 | ✅ | 查询与更新均带 `user_id` 条件 | `NotificationApiTest.cannotReadOthersNotification` |
| FR-9-4 | 未读计数与标记已读 | ✅ | `NotificationService.unreadCount` / `markRead` / `markAllRead` + 顶栏角标 | `NotificationApiTest.markReadBehaviour` |

### FR-10 界面与交互（UI）—— 地基与用户端已交付 / 团长、管理端待实施

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-10-1 | 设计系统（tokens / 状态映射 / 6 个公共组件） | ✅ | `styles/tokens.css`、`constants/status.js`、`components/`（6 个） | 提交 `19c5049`；设计稿对照 `docs/ui-design/` |
| FR-10-2 | 用户端主链路界面（广场 / 详情 / 订单 / 消息） | ✅ | `UserGroups` / `GroupBuyDetail` / `UserOrders` / `MyNotifications` 等 | 提交 `205f487`；预览图 `docs/ui-design/preview/` |
| FR-10-3 | 团长端界面（我的团购 / 核销台专注模式） | ⬜ | 现为裸 Element Plus（`LeaderGroups.vue` / `LeaderVerify.vue`） | — |
| FR-10-4 | 管理端界面（看板 KPI / 商品管理改良） | ⬜ | 现为裸 Element Plus（`AdminDashboard.vue` / `AdminProducts.vue`） | — |
| FR-10-5 | 响应式移动端（<768px / Tab Bar / ≥44px 触摸目标） | ⬜ | 11 屏设计稿就绪（`prototype-mobile.html`） | — |

---

## 2. 后端接口清单（29 个）

> 权限三档：公开 / 登录 / 角色（LEADER、ADMIN）。「实现位置」为 Controller 文件；「验证」为自动化用例或实测断言来源。

| # | 方法 | 路径 | 权限 | 实现位置 | 验证 |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | `/api/health` | 公开 | `HealthController` | README 快速开始 |
| 2 | POST | `/api/auth/register` | 公开 | `AuthController` | `AuthApiTest`（4 用例） |
| 3 | POST | `/api/auth/login` | 公开 | `AuthController` | `AuthApiTest`（2 用例） |
| 4 | GET | `/api/auth/me` | 登录 | `AuthController` | — |
| 5 | GET | `/api/products` | 登录 | `ProductController` | 前端 `ProductList.vue` |
| 6 | GET | `/api/products/{id}` | 登录 | `ProductController` | `ReviewApiTest.detailEndpointsExposeRating` |
| 7 | POST | `/api/products` | ADMIN | `ProductController` | verification-report §三 |
| 8 | PUT | `/api/products/{id}` | ADMIN | `ProductController` | verification-report §三（改价立即生效） |
| 9 | DELETE | `/api/products/{id}` | ADMIN | `ProductController` | verification-report §三 |
| 10 | GET | `/api/group-buys` | 登录 | `GroupBuyController` | `GroupBuyFlowApiTest.listContainsLeaderAndPickupPoint` |
| 11 | GET | `/api/group-buys/mine` | LEADER | `GroupBuyController` | — |
| 12 | GET | `/api/group-buys/{id}` | 登录 | `GroupBuyController` | `GroupBuyFlowApiTest.listAndDetailExposeProductAndPrice` |
| 13 | POST | `/api/group-buys` | LEADER | `GroupBuyController` | `GroupBuyFlowApiTest.createGroupBuyRequiresLeaderRole` |
| 14 | GET | `/api/group-buys/{id}/orders` | LEADER | `GroupBuyController` | `GroupBuyFlowApiTest`（3 用例：归属与权限） |
| 15 | POST | `/api/orders` | 登录 | `OrderController` | `GroupBuyFlowApiTest.orderRejectedWhenStockInsufficient` |
| 16 | POST | `/api/orders/{id}/pay` | 登录 | `OrderController` | verification-report §三 |
| 17 | POST | `/api/orders/{id}/cancel` | 登录 | `OrderController` | `GroupBuyFlowApiTest.cancelOrderRollsBackMemberCount` |
| 18 | GET | `/api/orders/mine` | 登录 | `OrderController` | `ReviewApiTest.orderListExposesReviewedFlag` |
| 19 | POST | `/api/orders/verify` | LEADER | `OrderController` | verification-report §三（核销 3 项） |
| 20 | POST | `/api/orders/{id}/refund` | ADMIN | `OrderController` | `GroupBuyFlowApiTest.duplicateRefundRejected` |
| 21 | POST | `/api/reviews` | 登录 | `ReviewController` | `ReviewApiTest`（3 用例） |
| 22 | GET | `/api/reviews/mine` | 登录 | `ReviewController` | — |
| 23 | GET | `/api/reviews/product/{id}` | 登录 | `ReviewController` | `ReviewApiTest.ratingAggregation` |
| 24 | GET | `/api/notifications` | 登录 | `NotificationController` | `NotificationApiTest`（隔离用例） |
| 25 | GET | `/api/notifications/unread-count` | 登录 | `NotificationController` | `NotificationApiTest.markReadBehaviour` |
| 26 | POST | `/api/notifications/{id}/read` | 登录 | `NotificationController` | `NotificationApiTest.cannotReadOthersNotification` |
| 27 | POST | `/api/notifications/read-all` | 登录 | `NotificationController` | `NotificationApiTest.markReadBehaviour` |
| 28 | GET | `/api/admin/dashboard` | ADMIN | `AdminController` | verification-report §三（与直查一致） |
| 29 | POST | `/api/upload` | ADMIN | `FileController` | verification-report §五（403 / 200） |

---

## 3. 前端页面清单（14 个）

| 页面 | 路由 | 视觉状态 | 说明 |
| --- | --- | --- | --- |
| Login.vue | `/login` | ✅ 设计系统 | 品牌叙事 + 表单，注册入口 |
| Register.vue | `/register` | 🔸 待精修 | 功能完整（角色可选 / 团长须填提货点）；视觉为 EP 默认，未与登录页风格统一 |
| Dashboard.vue | 布局 | ✅ 设计系统 | 按角色分组导航（一期），窄屏折叠 |
| Home.vue | `/` | ✅ 设计系统 | 欢迎条 + 数据概览 + 快捷入口 |
| UserGroups.vue | `/user/groups` | ✅ 设计系统 | 团购广场（二期） |
| GroupBuyDetail.vue | `/user/groups/:id` | ✅ 设计系统 | 团购详情（二期，726 行） |
| UserOrders.vue | `/user/orders` | ✅ 设计系统 | 我的订单：取货码票据 + 状态时间轴 + 评价入口 |
| MyNotifications.vue | `/user/notifications` | ✅ 设计系统 | 消息中心：五类图标 + 未读高亮 |
| ProductList.vue | `/user/products` | 🔸 简版 | 搜索筛选 + 空状态齐备；卡片样式未按设计稿精修 |
| ProductDetail.vue | `/user/products/:id` | 🔸 简版 | 含评分展示；视觉未精修 |
| LeaderGroups.vue | `/leader/groups` | ⬜ 裸 EP | 功能可用，三期按设计稿改造 |
| LeaderVerify.vue | `/leader/verify` | ⬜ 裸 EP | 功能可用（21 行极简），三期改造为「核销台专注模式」 |
| AdminProducts.vue | `/admin/products` | ⬜ 裸 EP | 功能可用，三期改造（搜索筛选 / 库存可视化） |
| AdminDashboard.vue | `/admin/dashboard` | ⬜ 裸 EP | 基础 KPI（31 行），三期改造为完整看板 |

公共组件（`src/components/`）：`StatusBadge` / `GroupBuyCard` / `OrderCard` / `StatCard` / `EmptyState` / `SkeletonList`。

---

## 4. 待实施计划（答辩口径：已规划）

### 阶段三：团长端 + 管理端界面改造

- `LeaderGroups.vue`：经营 KPI、团购列表（差几人 / 核销数）
- `LeaderVerify.vue`：核销台专注模式（大输入框 / 大按钮 / 最近核销记录）
- `AdminDashboard.vue`：KPI 卡 + 趋势折线 + 状态环形 + 最近订单（同时落地 FR-7-3 看板扩展）
- `AdminProducts.vue`：搜索筛选、数据表格、库存可视化（低于 20% 转红）

### 阶段四：响应式移动端

- `<768px` 断点 + 底部 Tab Bar + 底部固定操作栏 + `env(safe-area-inset-bottom)`
- 11 屏移动设计稿已就绪（`docs/ui-design/prototype-mobile.html`）：含取货码全屏票据、核销台自定义数字键盘

### 可选优化项（低优先级）

- 登录 / 注册视觉统一；商品浏览 / 详情页视觉精修（对齐设计稿）
- 列表分页（当前数据量小）、前端按需引入减包（当前主包约 1.27MB）
- JWT 主动失效（黑名单 / refresh token）、取货码碰撞重试策略演进

---

## 5. 质量与过程资产

| 资产 | 内容 | 位置 |
| --- | --- | --- |
| 自动化测试 | 4 个测试类 / 30 个用例 | `backend/src/test/` |
| 规格实测报告 | 48 项断言，3 个真实缺陷已修复转回归 | `docs/verification-report.md` |
| 需求基线 | 9 capability / 35 req / 57 scenario | `openspec/specs/` |
| 变更归档 | 2 个变更（提案 / 设计 / 任务 / delta 规格） | `openspec/changes/archive/` |
| 设计资产 | 设计规范 + 双端原型 + 31 张预览图 | `docs/ui-design/` |

测试用例分布：

| 测试类 | 用例数 | 覆盖 |
| --- | --- | --- |
| `AuthApiTest` | 8 | 登录 / 注册 / 401 / 404 / 405 / 越权注册管理员 |
| `GroupBuyFlowApiTest` | 10 | 列表字段、库存、取消回退人数、退款守卫、超时关单、团长订单权限 |
| `ReviewApiTest` | 7 | 评价资格、重复 / 越界、评分汇总、无评价不误导 |
| `NotificationApiTest` | 5 | 五类事件通知、跨用户隔离、未读数 |
| **合计** | **30** | |

---

## 6. 需求边界（明确不做）

真实支付（`PayService` 扩展点预留）｜商家入驻 / 配送 / 佣金｜限购 / 优惠券 / 秒杀｜评价审核与敏感词 / 评价修改删除｜短信、邮件、App 推送（仅站内信）｜WebSocket 实时推送｜消息免打扰设置｜App / 小程序（移动端为响应式 Web）。
