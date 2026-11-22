# FreshCommunity 完整闭环优化与交付总结（Walkthrough）

> **完成时间**：2026-11-22
> **对应计划**：[`docs/delivery/闭环优化与交付实施计划.md`](闭环优化与交付实施计划.md)

---

## 一、 核心业务闭环与交互修复明细

### 1. 团长端「发起团购」闭环修复
- **[`frontend/src/views/LeaderGroups.vue`](../../frontend/src/views/LeaderGroups.vue)**：
  - `loadProducts()` 改为请求 `listProducts({ size: 50 })` 并提取 `res.data?.records || []`，修复了 `GET /api/products` 升级为 `PageResult` 分页结构后把分页对象直接赋给数组导致的下拉选项异常与 `products.value.find is not a function` 报错，并完整覆盖演示库全部 41 个在售商品。
  - `<el-date-picker>` 增加 `value-format="YYYY-MM-DDTHH:mm:ss"`，避免前端将 `Date` 序列化为带 `Z` 的 UTC 时间串而引发后端 `LocalDateTime` 反序列化失败或 8 小时时区偏移。
  - `submit()` 增加必填项校验提示与创建成功后的 `ElMessage.success('团购已发起')` 反馈。
- **[`backend/src/main/java/com/freshcommunity/service/GroupBuyService.java`](../../backend/src/main/java/com/freshcommunity/service/GroupBuyService.java)**：
  - `createGroupBuy` 增加商品上架状态校验（`product.getStatus() == null || product.getStatus() != 1` 时抛出 `"商品已下架，无法发起团购"`），堵住通过接口对已下架商品开团的漏洞。

### 2. 成团发码后「待取货 / 出示取货码」全链路状态口径统一
后端成团发码（`OrderMapper.assignPickupCode`）与演示种子数据（`db/seed.sql`）中，已成团待自提订单状态为 `'SUCCESS'`（`'PENDING_PICKUP'` 为历史兼容状态）。本次统一了前后端 4 处状态判定：
- **[`frontend/src/constants/status.js`](../../frontend/src/constants/status.js)**：新增 `isPendingPickupStatus(status)`（匹配 `'SUCCESS'` 与 `'PENDING_PICKUP'`）与 `matchesOrderStatusTab(status, tabValue)`。
- **[`frontend/src/components/OrderCard.vue`](../../frontend/src/components/OrderCard.vue)**：将 `canViewCode` 改为 `['SUCCESS', 'PENDING_PICKUP'].includes(props.order.status) && !!props.order.pickupCode`，使已成团订单正常显示「出示取货码」按钮并能弹出 6 位取货凭证弹窗。
- **[`frontend/src/views/UserOrders.vue`](../../frontend/src/views/UserOrders.vue)**：「我的订单」页标签计数与列表过滤统一使用 `matchesOrderStatusTab`，切到「待取货」标签即可看到所有已成团待自提订单。
- **[`frontend/src/views/Home.vue`](../../frontend/src/views/Home.vue)**：用户首页「待取货」统计卡片使用 `isPendingPickupStatus(o.status)`，准确反映待取货订单数。
- **[`backend/src/main/java/com/freshcommunity/mapper/OrderMapper.java`](../../backend/src/main/java/com/freshcommunity/mapper/OrderMapper.java)**：`pageForAdmin` 在传入 `status = 'PENDING_PICKUP'` 时兼容匹配 `o.status = 'SUCCESS'`，确保管理端按「待取货」筛选与导出 CSV 数据完整。

### 3. 商品详情与消息中心交互体验补齐
- **[`frontend/src/views/ProductDetail.vue`](../../frontend/src/views/ProductDetail.vue)**：监听 `route.params.id` 变化重新加载商品详情、评价与同类推荐，并重置轮播首图索引，修复点击底部「看了又看」推荐商品不刷新的问题。
- **[`frontend/src/views/MyNotifications.vue`](../../frontend/src/views/MyNotifications.vue)**：在消息分类筛选中新增「AI 导购提醒」（`SUBSCRIPTION_MATCH`），完整覆盖全部 6 类站内通知。

### 4. 第二阶段：冲刺「优秀毕业设计」深度加固项
- **取货码核销防暴力枚举限流（[`OrderService.java`](../../backend/src/main/java/com/freshcommunity/service/OrderService.java)）**：为 `verifyPickupCode` 增加 5 分钟滑动时间窗防暴力枚举机制——同一团长连续 5 次核销失败后自动锁定 5 分钟（返回 400 `"核销失败次数过多，请稍后再试"`），核销成功即清零计数，兑现答辩指南 Q3 的第四重安全防线。
- **进行中团购的商品禁止下架或删除（[`GroupBuyMapper.java`](../../backend/src/main/java/com/freshcommunity/mapper/GroupBuyMapper.java) & [`ProductController.java`](../../backend/src/main/java/com/freshcommunity/controller/ProductController.java)）**：管理员下架或删除商品时前置校验 `countOngoingByProduct`，防止正在开团的商品被误删或下架导致团购异常。
- **管理端订单 CSV 导出突破 50 条分页截断（[`OrderService.java`](../../backend/src/main/java/com/freshcommunity/service/OrderService.java) & [`AdminController.java`](../../backend/src/main/java/com/freshcommunity/controller/AdminController.java)）**：新增 `listForAdminExport`（上限 5000 行），避免复用 `pageForAdmin` 被 `Math.min(size, 50)` 截断。
- **RESTful 畸形入参 400 统一拦截 & 测试假阳性修复（[`GlobalExceptionHandler.java`](../../backend/src/main/java/com/freshcommunity/common/GlobalExceptionHandler.java) & [`ReviewInsightsTest.java`](../../backend/src/test/java/com/freshcommunity/ReviewInsightsTest.java)）**：拦截缺失必填参数、类型转换异常与非法 JSON 返回 HTTP 400；修复 `ReviewInsightsTest.otherProductId` 遍历分页结构漏写 `.path("records")` 的问题。
- **前端取货码票据文案优化、核销台提示补全 & Vite 生产打包零告警（[`OrderCard.vue`](../../frontend/src/components/OrderCard.vue)、[`LeaderVerify.vue`](../../frontend/src/views/LeaderVerify.vue) & [`vite.config.js`](../../frontend/vite.config.js)）**：优化已成团发码订单的自提提示文案，在核销台安全规则列表补充连续 5 次核销失败触发 5 分钟锁定的说明，并将 `@element-plus/icons-vue` 拆分为独立 chunk，消除 `npm run build` 体积告警。

---

## 二、 自动化测试补强与验证结果

### 1. 新增自动化测试用例
- **[`OrderConcurrencyApiTest.java`](../../backend/src/test/java/com/freshcommunity/OrderConcurrencyApiTest.java)**：新增 `eightThreadsCompeteForLastSingleStockOnlyOneSucceeds`（单件库存 8 线程并发下单，验证仅 1 单返回 200、7 单返回 400“库存不足”、数据库剩余库存严格为 0）。
- **[`GroupBuyFlowApiTest.java`](../../backend/src/test/java/com/freshcommunity/GroupBuyFlowApiTest.java)**：新增 `cannotCreateGroupBuyForOffShelfProduct`（下架商品发起团购返回 400）。
- **[`OrderDetailApiTest.java`](../../backend/src/test/java/com/freshcommunity/OrderDetailApiTest.java)**：新增 `adminOrdersPendingPickupFilterIncludesSuccessOrders`（管理端按 `PENDING_PICKUP` 筛选包含 `SUCCESS` 成团待取货订单）。
- **[`LeaderConsoleApiTest.java`](../../backend/src/test/java/com/freshcommunity/LeaderConsoleApiTest.java)**：新增 `verifyPickupCodeRateLimitsBruteForceAttempts`（连续 5 次核销失败触发临时锁定，重置后正常核销）。
- **[`ProductSearchApiTest.java`](../../backend/src/test/java/com/freshcommunity/ProductSearchApiTest.java)**：新增 `cannotOffShelfOrDeleteProductWithOngoingGroupBuy`（在团商品禁止下架或删除）。
- **[`AuthApiTest.java`](../../backend/src/test/java/com/freshcommunity/AuthApiTest.java)**：新增 `missingOrMismatchParamReturns400`（缺失必填参数或类型不匹配返回 400）。
- **[`CatalogReviewsAdminTest.java`](../../backend/src/test/java/com/freshcommunity/CatalogReviewsAdminTest.java)**：新增 `csvExportExceedsPageLimitOfFifty`（突破 50 条分页上限，验证 55 笔订单可全量导出 CSV）。
- **[`frontend/tests/format.test.mjs`](../../frontend/tests/format.test.mjs)**：新增 `isPendingPickupStatus` 与 `matchesOrderStatusTab` 单元测试。

### 2. 实测验证结果
- **后端全量集成测试**（独立 MySQL 测试库 `freshcommunity_test`）：
  - 命令：`cd backend; $env:MAVEN_OPTS="-Dfile.encoding=UTF-8"; mvn -q test`
  - 结果：**19 个测试类，共 132 项测试全部通过（0 Failures, 0 Errors, 0 Skipped）**。
- **前端单元测试与生产构建**：
  - 命令：`cd frontend && npm test && npm run build`
  - 结果：**13 项前端单测全部通过**，Vite 生产打包**零告警**构建成功。

---

## 三、 毕设交付文档对齐完成项

已将以下文档与最新代码实现和 132+13 实测结果全面对齐：
- [`README.md`](../../README.md)（修正 11 张表描述、更新测试统计与闭环修复记录）
- [`docs/design-document.md`](../design-document.md)（补齐 11 张表、不同用户去重口径与 `uk_order_pickup_code` 唯一索引）
- [`docs/项目进度.md`](../项目进度.md)
- [`docs/thesis-material.md`](../thesis-material.md)
- [`docs/delivery/材料核对与实测记录.md`](材料核对与实测记录.md)
- [`docs/delivery/毕业设计论文_初稿.md`](毕业设计论文_初稿.md)（对齐 19 类 132 项测试矩阵、不同用户去重计数、取货码唯一索引重试与防暴力枚举限流、单库存 8 线程并发实测及模拟退款/AI 降级边界）
- [`docs/delivery/毕业答辩高频问题与满分应答指南.md`](毕业答辩高频问题与满分应答指南.md)
- [`docs/delivery/毕业答辩PPT大纲与演讲逐字稿.md`](毕业答辩PPT大纲与演讲逐字稿.md)
