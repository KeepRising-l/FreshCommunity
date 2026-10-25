# Design

## Context

- `MyBatisPlusConfig` 已注册 `PaginationInnerInterceptor(DbType.MYSQL)`，但全项目 `Page<T>` 零使用——分页基础设施就绪，只差消费。
- `product` 表无 `category` / `description`；`Order` 实体无商品/团购/团长冗余字段，前端 `UserOrders.vue` 用全量商品列表做 `productId → name` 映射。
- 手写 `@Select` 不会被 MyBatis-Plus 自动追加逻辑删除条件（项目既有约定：显式带 `deleted = 0`）。
- 前端设计体系（`tokens.css` + `StatCard` / `EmptyState` / `SkeletonList` / `StatusBadge` + `constants/status.js`）成熟，旧页面（ProductList / ProductDetail / AdminProducts / Register）未接入。
- 约束：不新增前后端依赖；毕设规模（演示数据 17 个商品、30 笔订单），不需要 ES / 分类表 / 读扩散。

## Goals / Non-Goals

**Goals:**

- 商品浏览从"全量倒出来"变成"关键词 + 分类 + 排序 + 分页"的标准检索体验，服务端过滤（顶栏搜索不再依赖客户端过滤）
- 订单的可读上下文（商品、团购、团长、提货点）由后端一次性给全，前端删掉映射逻辑
- 修改密码与团长提货点维护成为账号自助能力
- 管理端补齐商品管理的表单质量与订单管理的操作入口
- 修复清单每项都有回归测试锁死

**Non-Goals:**

- 不建分类表 / 分类管理页（分类是商品上的自由文本 + 去重接口）
- 不做用户禁用与角色变更（JWT 无失效机制，改了也不生效，明确留待 token 策略演进）
- 不改 `POST /api/orders` 的 `@RequestParam` 风格（README 已文档化的既有契约，改动属破坏性变更）
- 不做图片压缩 / 云存储

## Decisions

### D1 分页统一用 MyBatis-Plus `Page` + 通用 `PageResult<T>`

新建 `common/PageResult<T>{records, total, current, size, pages}`。`GET /api/products` 与 `GET /api/admin/orders` 返回 `Result<PageResult<...>>`。参数默认 `page=1, size=12`（商品网格 3/4 列的整数倍），`size` 上限 50 防御性钳制。

替代方案：继续返回全量 `List` 由前端 `slice`——否决，检索词过滤必须发生在 SQL（`LIKE`），前端过滤的前提（全量已在手）正是要消灭的。

### D2 分类用自由文本列 + 去重接口，不建分类表

`product.category VARCHAR(32) NOT NULL DEFAULT '其他'`。管理员增改商品时必填（前端 `el-select` 允许 `allow-create`，给出常用建议项：水果/蔬菜/肉禽蛋/水产/蛋奶/粮油调味/其他）；`GET /api/products/categories` 返回在售商品的 `DISTINCT category`，保证前端 chips 与数据一致（不会出现筛出一个空分类）。

替代方案：独立 `category` 表 + 外键——否决，毕设规模下纯属过度设计，且会连带管理页与级联删除问题。

### D3 订单 VO 补全走批量查询，逐条补全禁止 N+1

新建 `dto/OrderVO extends` 订单全字段 + `productName` / `productImage` / `groupBuyTitle` / `leaderName` / `pickupPointName` / `pickupPointAddress`。`OrderService` 内提供 `List<OrderVO> enrich(List<Order>)`：一次 `selectBatchIds` 取商品与团购、一次取团长（user），Java 内组 map 回填。`/api/orders/mine`、`GET /api/orders/{id}`、`GET /api/admin/orders`、`GET /api/group-buys/{id}/orders` 四处复用。已逻辑删除的商品/团购/团长查不到时字段为 `null`，前端回退到占位视觉（既有 `productVisual` 已处理空标题）。

### D4 订单详情与权限：本人或管理员

`GET /api/orders/{id}`：订单不存在或非本人且非 ADMIN → 400「订单不存在」（与项目既有口径一致，不区分 404/403，避免探测）。管理员可看任意订单详情，服务端判断 `role == ADMIN` 放行——控制器从 `@RequestAttribute("role")` 取角色（`JwtAuthenticationFilter` 已塞入，需补一个 attribute）。

### D5 修改密码不使旧 token 失效

JWT 无黑名单，改密后旧 token 仍可用至过期（24h）。毕设可接受，接口文档与页面提示「修改成功后请牢记新密码」。校验：旧密码必须匹配（不匹配 400），新密码 6–32 位（与注册一致）。防弱校验：新旧密码不得相同。

### D6 上传接口加固

- 扩展名安全解析：`originalFilename` 为 null / 无 `.` → 400「文件名必须带扩展名」（替代现在直接 `substring(-1)` 崩溃）。
- 白名单：扩展名 ∈ {jpg,jpeg,png,gif,webp} **且** `Files.probeContentType` 或 MultipartFile `contentType` 以 `image/` 开头，二者都过才落盘；否则 400「仅支持图片文件」。
- 保留既有 `@PreAuthorize ADMIN` 与 UUID 文件名（防路径穿越）。

### D7 Security 过滤链统一 401/403 JSON

`SecurityConfig` 配 `exceptionHandling().authenticationEntryPoint()`（HTTP 401 + `Result.error(401,"未登录或登录已过期")`）与 `accessDeniedHandler()`（HTTP 403 + `Result.error(403,"无权限访问")`）。此前过滤层拒绝走 Spring 默认空体 403，前端拦截器要兼容两种格式；统一后所有响应都是 `{code,msg,data}`。前端 `request.js` 已按 HTTP 401 跳登录，行为不变、格式归一。注意 `@PreAuthorize` 的 403 仍走 `GlobalExceptionHandler`（HTTP 403 + JSON），两条路径殊途同归。

### D8 500 兜底脱敏

`GlobalExceptionHandler.handleException` 返回固定文案「服务开小差了，请稍后重试」并 `log.error` 完整堆栈；不再把 `e.getMessage()`（可能含 SQL/路径）直接外抛。

### D9 前端角色守卫与 404

- 路由表加 `meta.roles`：`/admin/** → ['ADMIN']`；`/leader/** → ['LEADER']`；`/user/** → ['USER','LEADER']`（团长也是社区居民，保留选购）；`/notifications`、`/profile` → 任意登录角色。守卫在校验角色不符时 `next('/dashboard')` 并 toast 提示——后端 `@PreAuthorize` 仍是最终防线，前端守卫只是体验层。
- 兜底路由 `{ path: '/:pathMatch(.*)*', component: NotFound }`。
- 已登录访问 `/login` / `/register` → 重定向 `/dashboard`。

### D10 商品列表排序白名单

`sort` 参数只接受 `latest`（默认，`created_at DESC`）/ `price_asc` / `price_desc`，其余值按 `latest` 处理——排序直接拼进 `orderBy`，白名单杜绝注入面。

### D11 管理端订单搜索口径

`keyword` 同时匹配 `order_no` 模糊 **或** 买家 `username` 模糊（join `user`）。管理员日常场景是「用户报订单号」或「某个用户的单子」，两个都能搜。状态筛选直接等值匹配。手写 SQL 分页：`SELECT ... LEFT JOIN user ... WHERE ... LIMIT`，配 `Page` 参数（MyBatis-Plus 手写 SQL 分页走 `Page` 参数传递，`IPage` 返回 total 自动 count）。

### D12 团长提货点维护的范围限制

`PUT /api/auth/profile` 仅接受 `pickupPointName` / `pickupPointAddress` 且仅 LEADER 角色可改（USER 传入忽略并提示）。名称非空、≤128；地址 ≤255。改完即时生效于后续团购详情（读时实时取 user 表，无缓存问题）。

## Risks / Trade-offs

- **seed 重导才有分类数据**：已有库执行 `migrate-2026-10-25.sql` 后所有存量商品分类为「其他」；演示时建议重导 seed。README 说明。
- **`description` 列表页不返回**（只有详情返回），避免列表响应膨胀；前端列表卡只展示名称/价格/分类/库存。
- **订单 VO 补全后 `/api/orders/mine` 响应变大**：字段纯增量，前端旧消费方式（`Order` 字段）不受影响。
- **401 统一后未登录页行为变化**：此前无 token 访问受保护接口返回 HTTP 403 空体，现在返回 HTTP 401 JSON——前端拦截器本就按 HTTP 401 处理跳转，实际是修复了「403 空体导致 toast 显示'网络错误'」的老毛病。测试里对无 token 断言需同步改为 401。
