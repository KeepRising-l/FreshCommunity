# Proposal

## Why

四轮变更交付后，核心闭环已经完整，但对照真实电商平台的日常使用面，仍有四块明显短板：

1. **商品目录没有检索能力**。`product` 表连 `category` 字段都没有，`GET /api/products` 一次返回全部在售商品，无关键词、无分类、无排序、无分页——分页拦截器（`PaginationInnerInterceptor`）早已配置却从未被使用。前端 `ProductList.vue` 还是旧版裸样式，顶栏搜索框传来的 `?keyword=` 只能靠客户端过滤全量列表。
2. **订单没有详情页，信息靠前端拼**。`GET /api/orders/mine` 只回 `Order` 实体（不含商品名/提货点），`UserOrders.vue` 被迫拉全量商品列表做 `productId → name` 映射——商品一旦删除映射即失效；全项目没有 `GET /api/orders/{id}`，订单的完整上下文（团购、团长、提货点）无处可看。
3. **账号不可维护**。没有任何修改密码的入口；团长注册后提货点名称/地址永远无法更改（改名了、搬店了只能重新注册）。
4. **管理端只有两页半**。`AdminProducts.vue` 无表单校验（可以提交空名称）、`api/upload.js` 封装好了却从未被接上（图片只能手填 URL）、无加载态无分页；管理员的退款接口 `POST /api/orders/{id}/refund` 存在但**前端没有任何入口**，"订单退款兜底"实际无法操作。

另有一批横切缺陷随本轮一并修复：上传接口对无扩展名/空文件名会直接抛 `StringIndexOutOfBoundsException` 且无类型白名单；未认证请求被 Spring Security 默认入口点拒绝，不走统一 `{code,msg,data}` 返回结构（前端两种格式要兼容处理）；`GET /api/auth/me` 对已删除用户抛 NPE 500；500 兜底直接外抛 `e.getMessage()` 泄漏内部细节；前端无角色路由守卫（USER 可直达 `/admin/dashboard` 只靠后端 403 兜底）、无 404 页；`GET /api/group-buys` 列表对每个商品逐个查口碑标签（N+1）。

## What Changes

- **商品目录增强**：`product` 表新增 `category` / `description` 字段；`GET /api/products` 支持 `keyword`（名称模糊）/ `category` / `sort`（最新、价格升、价格降）/ `page` / `size` 分页参数，返回分页结构；新增 `GET /api/products/categories` 返回在售商品的分类去重列表；新增 `GET /api/products/admin-list`（管理员）返回含下架商品的全量分页列表；管理端商品增改接口接受 `category` / `description`。前端 `ProductList.vue` / `ProductDetail.vue` 按新视觉规范重做（搜索、分类 chips、排序、分页、骨架屏、空态）。
- **订单详情与信息补全**：新增 `GET /api/orders/{id}`（本人或管理员），返回完整订单 VO；"我的订单"列表 VO 补全 `productName` / `productImage` / `groupBuyTitle` / `leaderName` / `pickupPointName`（批量查询补全，不做 N+1）；前端新增订单详情页 `/user/orders/:id`，`UserOrders.vue` 弃用全量商品映射、订单卡片可点击进入详情。
- **个人中心**：新增 `POST /api/auth/change-password`（校验旧密码、BCrypt 更新）；新增 `PUT /api/auth/profile`（团长维护本人提货点名称/地址）。前端新增 `/profile` 个人中心页（账号信息 + 修改密码 + 团长提货点维护），侧栏所有角色可见。
- **管理端补全**：`AdminProducts.vue` 重做（表单校验规则、图片上传接 `api/upload`、上下架开关、加载态、空态、分页）；新增订单管理页 `/admin/orders`（全量订单分页列表 + 状态筛选 + 订单号/买家搜索 + 退款操作入口），配套后端 `GET /api/admin/orders`。
- **横切修复**（每项补回归测试）：上传接口扩展名安全解析 + 图片类型白名单；Security 过滤链统一 401/403 JSON 返回；`/api/auth/me` 空结果 401 而非 500；500 兜底不再外抛异常消息；团购列表口碑标签改批量查询；前端角色路由守卫（`meta.roles`）+ 404 兜底路由 + 已登录访问登录页重定向；`UserGroups.vue` 的假排序控件改为真实排序。
- **明确不做**：商品分类管理页（分类为自由文本 + 去重列表，不建分类表）、用户禁用/角色变更（涉及 JWT 失效策略，超出本轮）、真实图片存储服务（仍存本地 `uploads/`）、评价管理、订单导出。

## Capabilities

### New Capabilities

<!-- 无：四块改动分别落在既有能力边界内。 -->

### Modified Capabilities

- `product-catalog`: 「商品浏览」从全量列表升级为可检索（关键词/分类/排序）+ 分页；新增商品分类与描述字段及其维护与展示口径
- `order-lifecycle`: 新增「订单详情」（本人或管理员可查、含完整上下文）与「订单信息补全」（我的订单列表自带商品/团购/提货点冗余字段）；「管理员手动退款」补充管理端订单列表能力（全量、可筛选、可搜索、分页）
- `user-roles`: 新增「修改密码」与「团长维护提货点」两个账号自助维护需求

## Impact

- **数据库**：`product` 表 `ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT '其他'`、`ADD COLUMN description VARCHAR(500)`；`db/schema.sql` 与 `db/seed.sql` 同步（seed 为 17 个商品补分类与描述），另附增量脚本 `db/migrate-2026-10-25.sql` 供已有库升级
- **后端**：`ProductController` / `OrderController` / `AuthController` / `AdminController` / `FileController` / `SecurityConfig` / `GlobalExceptionHandler`；新增 `dto/PageResult` / `dto/OrderVO` / `dto/ChangePasswordRequest` / `dto/ProfileUpdateRequest`；`OrderService` / `GroupBuyController` / `ReviewAnalysisService` 配套调整
- **前端**：重做 `ProductList.vue` / `ProductDetail.vue` / `AdminProducts.vue` / `UserOrders.vue`（局部） / `UserGroups.vue`（局部） / `Register.vue`（视觉对齐）；新增 `OrderDetail.vue` / `Profile.vue` / `AdminOrders.vue` / `NotFound.vue`；`router/index.js` 加角色守卫与 404；`api/` 四个模块补方法；`components/OrderCard.vue` 改为直接消费订单 VO 冗余字段；不新增依赖
- **测试**：现有 67 个用例须全绿；新增商品检索分页 / 订单详情与补全 / 修改密码与提货点维护 / 管理端订单列表 / 上传白名单 / 统一 401 口径等集成用例
- **文档**：`README.md`（功能表、接口一览、测试数）、`docs/implementation-inventory.md`、`docs/verification-report.md` 追加本轮实测
