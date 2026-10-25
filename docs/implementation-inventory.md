# FreshCommunity 实现清单（需求 → 实现对照）

> **用途**：答辩展示「做了什么、怎么做、还差什么」；与 `docs/requirements-analysis.md` 的功能编号逐条对应。
> **状态口径**：✅ 已交付（功能可用且有验证）｜🔸 已交付 · 视觉简版｜🛠️ 代码已写、验证待补｜⬜ 待实施（已规划未开发）
> **数据截至**：2026-10-25（源码数量按当前目录统计；测试运行结果为最近一次 `mvn test` 实测：94 用例全绿）

---

## 0. 总览

| 维度 | 数量 | 说明 |
| --- | --- | --- |
| 功能能力（capability） | **11 个基线能力已交付** | 现行基线 60 条 requirement / 133 个 scenario（2026-10-23 归档并入 +7 req / +21 scenario；2026-10-25 归档并入 +12 req / +33 scenario，含改写） |
| 后端接口 | **43 个** | 10 个 Controller，统一 `{code, msg, data}` 返回；本轮无新接口（可靠性为内部机制 + 通知字段增量） |
| 数据库表 | **9 张** | 7 张交易与配套表 + `user_subscription` + `review_analysis` |
| 前端页面 | **19 个** | 含订单详情 / 个人中心 / 订单管理 / 404 页；另有 7 个公共组件 |
| 自动化测试 | **94 个测试方法** | 14 个测试类；最近一次实测（2026-10-25）`mvn test` 全绿 |
| 规格实测 | **48 项断言 + AI 导购端到端 + 看板/团长端 16 项 + 目录检索/订单/个人中心 33 项 + 订阅匹配可靠性 10 项** | `docs/verification-report.md`（§一～五 为 MVP 基线；§六 AI 导购；§七 看板与团长端；§八 目录检索与订单/个人中心/管理端；§九 订阅匹配可靠性） |
| 待实施 / 待验证 | **P2 匹配可靠性与 P4 移动端适配均已收尾（2026-10-25）** | 剩余仅 JWT 主动失效（黑名单 / refresh token，毕设口径可接受） |

---

## 1. 功能实现对照（按需求编号）

### FR-1 用户与权限（user-roles）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-1-1 | 自助注册（用户名唯一 / BCrypt / 团长需提货点 / 拒绝 ADMIN） | ✅ | `AuthController.register` + `RegisterRequest` + `Register.vue` | `AuthApiTest`：registerSuccess / registerDuplicateUsernameRejected / registerAdminRejected / registerLeaderRequiresPickupPoint |
| FR-1-2 | 登录与 JWT 身份识别 | ✅ | `AuthController.login` / `me` + `JwtUtil` | `AuthApiTest`：loginSuccess / loginWrongPasswordReturns401 |
| FR-1-3 | 角色权限隔离 | ✅ | `SecurityConfig` + 各 Controller `@PreAuthorize` + 归属校验 | `GroupBuyFlowApiTest`：userCannotListGroupOrders / otherLeaderCannotListGroupOrders + verification-report §三 |
| FR-1-4 | 团长提货点资料（含本人维护） | ✅ | `user` 表字段 + 团购详情 VO + `PUT /api/auth/profile` + `Profile.vue` | `GroupBuyFlowApiTest.listContainsLeaderAndPickupPoint` + `ProfileApiTest.leaderUpdatesPickupPoint`（改后团购详情即时生效） |
| FR-1-5 | 修改密码 | ✅ | `POST /api/auth/change-password` + `Profile.vue` | `ProfileApiTest`：改密成功后旧密码 401 / 新密码 200、旧密码错误 400、新旧相同 400 |

### FR-2 商品管理（product-catalog）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-2-1 | 商品上架 | ✅ | `ProductController`（POST）+ `AdminProducts.vue` | verification-report §三（缺必填字段返回 400） |
| FR-2-2 | 商品编辑（改价立即生效） | ✅ | `ProductController`（PUT / DELETE） | verification-report §三（改价立即生效） |
| FR-2-3 | 商品浏览（关键词 / 分类 / 排序 / 分页检索 + 详情 + 评分汇总） | ✅ | `ProductController`（GET 检索分页 + /categories + admin-list）+ `ProductList.vue` / `ProductDetail.vue`（按设计系统重做） | `ProductSearchApiTest`（7 用例：分页/关键词/分类/排序白名单/逻辑删除/管理端列表/默认分类）+ verification-report §八 |
| FR-2-4 | 商品图片上传（≤5MB，仅管理员，图片白名单） | ✅ | `FileController`（扩展名+contentType 双校验）+ `AdminProducts.vue`（el-upload 接入） | `UploadAndSecurityApiTest`（txt 拒 / 无扩展名拒 / 假类型拒 / PNG 成功）+ verification-report §八 |

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
| FR-5-X | 订单详情与信息补全 | ✅ | `GET /api/orders/{id}`（本人或管理员）+ `OrderVO` 批量补全商品/团购/团长/提货点 + `OrderDetail.vue` | `OrderDetailApiTest`（5 用例：详情权限隔离/补全字段/删商品不报错/管理端搜索分页/权限） |
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

### FR-7 数据看板（admin-dashboard）—— 全部交付

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-7-1 | 今日订单数 | ✅ | `AdminController.dashboard` → `AdminDashboardService` | verification-report §三（与数据库直查一致）、§七 |
| FR-7-2 | 今日成团数 | ✅ | 同上 | verification-report §三、§七 |
| FR-7-3 | 看板扩展（成交额 / 注册用户 / 进行中团购 / 趋势 / 状态分布） | ✅ | `AdminDashboardVO` + `AdminDashboardService`（7 日补零）+ `AdminDashboard.vue`（手绘 SVG） | `DashboardApiTest`（7 用例）+ verification-report §七（KPI / 分布 / 趋势 / 空态 / 窄容器） |

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

### FR-11 AI 导购（ai-shopping-assistant）—— 全部交付（变更已于 2026-10-17 归档）

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-11-1 | 预览解析条件、确认后创建订阅 | ✅ | `LlmParseService` / `RuleDemandParseService`、`SubscriptionController`、`AiShoppingAssistant.vue` | `SubscriptionApiTest`：previewDoesNotCreate / createUsesConfirmedCondition；`vite build` 通过 |
| FR-11-2 | 新团匹配并发送站内消息 | ✅ | `GroupBuyService`、`SubscriptionService` | `SubscriptionApiTest`：newGroupBuyNotifiesSubscriber / missNoNotify |
| FR-11-3 | 创建订阅时匹配已有团 | ✅ | `SubscriptionService.matchAgainstOngoing` | `SubscriptionApiTest`：subscribeMatchesExisting |
| FR-11-4 | 查看与取消本人订阅 | ✅ | `SubscriptionController`、`AiShoppingAssistant.vue` | `SubscriptionApiTest.listAndCancelOwnership`；`vite build` 通过 |
| FR-11-5 | LLM 优先与规则降级 | ✅ | `AiClient`（OpenAI 兼容客户端）、`LlmParseService`（`@Primary`，LLM 优先→规则回落）、`AiProperties` | `LlmParseServiceTest`：noKeyFallsBackToRule / noKeyUnparseable / noKeyNoPrice |
| FR-11-6 | 评价情感与标签洞察 | ✅ | `ReviewAnalysisService`、`review_analysis` 表、`ScheduledJobs.analyzePendingReviews` | `ReviewInsightsTest`：analyzeOnceIdempotent / analyzeNegative / pendingBatchSkipsBlank / productDetailExposesTags / notifyIncludesInsightSummary / neutralReviewProducesNoTags |
| FR-11-7 | 开团指定拼团价（订阅价格匹配的真实输入） | ✅ | `GroupBuyCreateRequest.price` + `GroupBuyService.resolvePrice` + `LeaderGroups.vue` | `GroupBuyFlowApiTest`：createGroupBuyWithCustomPrice / customPriceValidation；`vite build` 通过 |
| FR-11-X | 匹配可靠性与提醒跳转（唯一闸门 / 补偿扫描 / refGroupBuyId） | ✅ | `subscription_match` 表 + `SubscriptionService.tryMatchAndNotify` + `ScheduledJobs.compensateSubscriptionMatches` + `MyNotifications.vue` 跳转 | `SubscriptionMatchReliabilityTest`（5 用例）+ verification-report §九（含真实后端 75 秒内补偿补齐与幂等实测） |

> **P2 匹配可靠性**（并发创建订阅与团购可能漏提醒）属 design.md 记录的首版已知缺口，明确不在本变更范围，留待后续演进。

### FR-10 界面与交互（UI）—— 全部交付（含 2026-10-25 移动端适配）

| 编号 | 功能点 | 状态 | 实现位置 | 验证证据 |
| --- | --- | --- | --- | --- |
| FR-10-1 | 设计系统（tokens / 状态映射 / 6 个公共组件） | ✅ | `styles/tokens.css`、`constants/status.js`、`components/`（6 个） | 提交 `19c5049`；设计稿对照 `docs/ui-design/` |
| FR-10-2 | 用户端主链路界面（广场 / 详情 / 订单 / 消息） | ✅ | `UserGroups` / `GroupBuyDetail` / `UserOrders` / `MyNotifications` 等 | 提交 `205f487`；预览图 `docs/ui-design/preview/` |
| FR-10-3 | 团长端界面（我的团购经营 KPI / 差几人 / 核销进度；核销台专注模式 / 最近核销） | ✅ | `LeaderGroups.vue`、`LeaderVerify.vue` | `LeaderConsoleApiTest`（8 用例）+ verification-report §七 |
| FR-10-4 | 管理端看板界面（KPI / 趋势 / 状态分布） | ✅ | `AdminDashboard.vue` | `DashboardApiTest` + verification-report §七 |
| FR-10-6 | 管理端商品管理（搜索筛选 / 校验 / 图片上传 / 上下架开关 / 分页） | ✅ | `AdminProducts.vue` 重做（tokens 设计系统） | verification-report §八（管理端列表与上传实测）；`npm run build` 通过 |
| FR-10-7 | 管理端订单管理（全量列表 / 状态筛选 / 订单号与买家搜索 / 退款入口） | ✅ | `AdminOrders.vue` + `GET /api/admin/orders` | `OrderDetailApiTest.adminOrders*`（3 用例）+ verification-report §八 |
| FR-10-8 | 个人中心（账号信息 / 修改密码 / 团长提货点维护） | ✅ | `Profile.vue` + `AuthController` 两个新接口 | `ProfileApiTest`（5 用例）+ verification-report §八 |
| FR-10-9 | 前端角色路由守卫 + 404 页 + 登录回跳 | ✅ | `router/index.js`（meta.roles 守卫 / catch-all / redirect query）+ `NotFound.vue` | `npm run build` 通过；user1 直达 /admin/** 被弹回 /dashboard |
| FR-10-5 | 响应式移动端（<768px / Tab Bar / ≥44px 触摸目标） | ✅ | `Dashboard.vue` 底部导航（按角色 ≤5 项）+ `GroupBuyDetail.vue` 固定操作栏 + `tokens.css` 移动层（弹窗 92% / 按钮触摸目标） | 浏览器 390×844 实测 9 项断言 + 1280 桌面回归（verification-report §十） |

---

## 2. 后端接口清单（43 个：本轮 +8）

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
| 28 | GET | `/api/admin/dashboard` | ADMIN | `AdminController` | `DashboardApiTest` + verification-report §三、§七 |
| 29 | POST | `/api/upload` | ADMIN | `FileController` | verification-report §五（403 / 200） |
| 30 | POST | `/api/subscriptions/parse` | 登录 | `SubscriptionController` | `SubscriptionApiTest.previewDoesNotCreate` |
| 31 | POST | `/api/subscriptions` | 登录 | `SubscriptionController` | `SubscriptionApiTest`：解析 / 创建 |
| 32 | GET | `/api/subscriptions` | 登录 | `SubscriptionController` | `SubscriptionApiTest`：本人列表隔离 |
| 33 | DELETE | `/api/subscriptions/{id}` | 登录 | `SubscriptionController` | `SubscriptionApiTest`：本人取消 / 跨用户拒绝 |
| 34 | GET | `/api/group-buys/mine/summary` | LEADER | `GroupBuyController` | `LeaderConsoleApiTest`：本人数据 / 零值 / 403 / 真实成团链路 |
| 35 | GET | `/api/orders/verify-history` | LEADER | `OrderController` | `LeaderConsoleApiTest`：倒序 10 条 / 归属隔离 / 无买家字段 |

---

## 3. 前端页面清单（19 个：本轮 +4）

| 页面 | 路由 | 视觉状态 | 说明 |
| --- | --- | --- | --- |
| Login.vue | `/login` | ✅ 设计系统 | 品牌叙事 + 表单，注册入口 |
| Register.vue | `/register` | ✅ 设计系统 | 与登录页同款渐变背景 + 身份选择卡（团长须填提货点） |
| Dashboard.vue | 布局 | ✅ 设计系统 | 按角色分组导航（一期），窄屏折叠 |
| Home.vue | `/` | ✅ 设计系统 | 欢迎条 + 数据概览 + 快捷入口 |
| UserGroups.vue | `/user/groups` | ✅ 设计系统 | 团购广场（二期） |
| GroupBuyDetail.vue | `/user/groups/:id` | ✅ 设计系统 | 团购详情（二期，含口碑标签区） |
| UserOrders.vue | `/user/orders` | ✅ 设计系统 | 我的订单：取货码票据 + 状态筛选 + 评价弹窗 + 卡片点击进详情（本轮消费订单 VO 冗余字段，弃用商品映射） |
| MyNotifications.vue | `/user/notifications` | ✅ 设计系统 | 消息中心：六类图标 + 未读高亮 |
| AiShoppingAssistant.vue | `/user/ai` | ✅ 设计系统 | 创建需求订阅、显示解析结果、查看与取消关注 |
| ProductList.vue | `/user/products` | ✅ 设计系统 | 服务端关键词/分类/排序检索 + 分页 + 骨架屏（本轮重做） |
| ProductDetail.vue | `/user/products/:id` | ✅ 设计系统 | 大图区 + 价格卡 + 分类/描述 + 口碑标签（本轮重做） |
| LeaderGroups.vue | `/leader/groups` | ✅ 设计系统 | 经营概览四卡 + 团购列表（还差几人 / 核销进度） |
| LeaderVerify.vue | `/leader/verify` | ✅ 设计系统 | 核销台专注模式：大号等宽输入 + 常驻结果条 + 安全说明 + 最近核销记录 |
| AdminProducts.vue | `/admin/products` | ✅ 设计系统 | 重做：表单校验 / 图片上传 / 上下架开关 / 分页（本轮）
| OrderDetail.vue | `/user/orders/:id` | ✅ 设计系统 | 订单详情：状态卡 + 取货码票据 + 商品/团购/提货点信息（本轮新增）
| Profile.vue | `/profile` | ✅ 设计系统 | 个人中心：账号信息 + 修改密码 + 团长提货点维护（本轮新增）
| AdminOrders.vue | `/admin/orders` | ✅ 设计系统 | 管理端订单管理：筛选/搜索/分页/退款入口（本轮新增）
| NotFound.vue | `/:pathMatch(.*)*` | ✅ 设计系统 | 404 友好页（本轮新增） |
| AdminDashboard.vue | `/admin/dashboard` | ✅ 设计系统 | 5 张 KPI 卡 + 手绘 SVG 7 日趋势 + 订单状态分布条 + 空态 |

公共组件（`src/components/`）：`StatusBadge` / `GroupBuyCard` / `OrderCard` / `StatCard` / `EmptyState` / `SkeletonList` / `ReviewDialog`（评价弹窗，列表与详情页共用）。

---

## 4. 后续优化优先级

### P0～P3：已完成（见 §1 FR-11）

- **P0 订阅闭环**：解析预览接口、AI 导购页、匹配提醒、后端全量回归 —— 已完成。
- **P1 LLM 解析链**：`AiClient`（OpenAI 兼容）+ `LlmParseService`（LLM 优先、规则降级）+ `ai.*` 配置 —— 已完成，无密钥环境仍可订阅。
- **P3 评价洞察**：`review_analysis` 表 + 批量分析任务 + 详情标签 + 提醒摘要 —— 已完成。
- **收尾（2026-10-17）**：端到端演示脚本在「无 key / LLM mock / LLM 不可达」三种模式下实测通过；期间修掉 5 个真实缺陷（演示脚本无对应商品、开团无法指定拼团价、中性评价写成口碑标签、规则词典单字误伤、否定表达被记成正面），均已补回归用例。
- ~~**遗留（P2）**：并发"创建订阅 / 创建团购"存在漏提醒窗口，且通知不含团购跳转引用~~ —— 已完成（2026-10-25 变更：`subscription_match` 唯一键闸门 + 60 秒补偿扫描 + `refGroupBuyId` 跳转引用，verification-report §九）

### P4：界面与容量扩展

- ~~`LeaderGroups.vue`：经营 KPI、团购列表（差几人 / 核销数）~~ —— 已完成（2026-10-23 变更）
- ~~`LeaderVerify.vue`：核销台专注模式（大输入框 / 大按钮 / 最近核销记录）~~ —— 已完成
- ~~`AdminDashboard.vue`：KPI 卡 + 趋势折线 + 状态分布（落地 FR-7-3 看板扩展）~~ —— 已完成（最近订单列表未做）
- ~~`AdminProducts.vue`：搜索筛选、数据表格、库存可视化~~ —— 已完成（2026-10-25 变更；库存可视化以低库存红字提示形式交付）
- `<768px` 断点 + 底部 Tab Bar + 底部固定操作栏 + `env(safe-area-inset-bottom)`
- 11 屏移动设计稿已就绪（`docs/ui-design/prototype-mobile.html`）：含取货码全屏票据、核销台自定义数字键盘
- ~~登录 / 注册视觉统一；商品浏览 / 详情页视觉精修（对齐设计稿）~~ —— 已完成（2026-10-25 变更）
- ~~列表分页（当前数据量小）~~ —— 商品/管理端订单/管理端商品已分页（2026-10-25 变更）；前端按需引入减包（当前主包约 1.27MB）待做
- JWT 主动失效（黑名单 / refresh token）、取货码碰撞重试策略演进

---

## 5. 质量与过程资产

| 资产 | 内容 | 位置 |
| --- | --- | --- |
| 自动化测试 | 14 个测试类 / 94 个测试方法 | `backend/src/test/` |
| 规格实测报告 | MVP 基线 48 项断言 + AI 导购端到端（2026-10-17）+ 看板与团长端 16 项（2026-10-23）+ 目录检索/订单/个人中心/管理端 33 项（2026-10-25）；累计 9 个真实缺陷已修复转回归 | `docs/verification-report.md` |
| 需求基线 | 11 capability / 42 req / 77 scenario | `openspec/specs/` |
| 变更归档 | 4 个已归档变更（最新：`2026-10-23-admin-dashboard-and-leader-ui`） | `openspec/changes/` |
| 设计资产 | 设计规范 + 双端原型 + 31 张预览图 | `docs/ui-design/` |

测试用例分布：

| 测试类 | 用例数 | 覆盖 |
| --- | --- | --- |
| `AuthApiTest` | 8 | 登录 / 注册 / 401 / 404 / 405 / 越权注册管理员 |
| `GroupBuyFlowApiTest` | 12 | 列表字段、库存、取消回退人数、退款守卫、超时关单、团长订单权限、拼团价指定与校验 |
| `ReviewApiTest` | 7 | 评价资格、重复 / 越界、评分汇总、无评价不误导 |
| `NotificationApiTest` | 5 | 六类事件通知、跨用户隔离、未读数 |
| `SubscriptionApiTest` | 9 | 需求解析、预览不落库、确认条件、归属隔离、新团与已有团匹配 |
| `LlmParseServiceTest` | 3 | 无 key 回落规则解析、非标表述返回 null、无价格需求 |
| `ReviewInsightsTest` | 8 | 情感与标签、重复分析幂等、批量跳过无文字评价、详情标签、提醒摘要、中性零标签、词典长词优先 |
| `DashboardApiTest` | 7 | 成交额口径、今日零值、软删排除、状态分布、7 日连续补零、平台规模、非管理员 403 |
| `LeaderConsoleApiTest` | 8 | 概览归属隔离与零值、真实成团→核销链路、403、核销记录倒序 10 条 / 归属 / 字段裁剪 |
| **合计** | **67** | 最近一次 `mvn test` 实测全绿 |

---

## 6. 需求边界（明确不做）

真实支付（`PayService` 扩展点预留）｜商家入驻 / 配送 / 佣金｜限购 / 优惠券 / 秒杀｜评价审核与敏感词 / 评价修改删除｜短信、邮件、App 推送（仅站内信）｜WebSocket 实时推送｜消息免打扰设置｜App / 小程序（移动端为响应式 Web）。
