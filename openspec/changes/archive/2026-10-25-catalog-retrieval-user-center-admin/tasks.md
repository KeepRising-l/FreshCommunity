# Tasks

## 实施顺序

数据库 → 后端三块（商品检索 / 订单 / 个人中心）可并行推进，修复清单穿插其中；每组后端接口落地后立刻补测试跑绿，再做前端消费页；最后统一端到端实测、同步文档并归档。降级顺序：先保 1+2+3（检索与订单，体验短板最大），再 4（个人中心）与 5（修复），最后 6+7（管理端与横切）。

## 1. 数据库

- [x] 1.1 `db/schema.sql`：`product` 表增加 `category VARCHAR(32) NOT NULL DEFAULT '其他'`、`description VARCHAR(500)`，3 条预置商品补分类，验证：`mysql < schema.sql` 重建成功且 `DESC product` 含新列
- [x] 1.2 `db/seed.sql`：17 个商品全部补 `category`（水果/蔬菜/肉禽蛋/水产/蛋奶/粮油调味）与一句话 `description`，验证：重导后 `SELECT category, COUNT(*) FROM product GROUP BY category` 分布合理（水果≥6）
- [x] 1.3 新建 `db/migrate-2026-10-25.sql` 增量脚本（两条 `ADD COLUMN`，重复执行会报错属预期，注释说明），验证：对已建库执行后新列存在且默认值生效

## 2. 商品检索（后端）

- [x] 2.1 `Product` 实体加 `category` / `description` 字段；新建 `common/PageResult<T>`，验证：`mvn compile` 通过
- [x] 2.2 `GET /api/products` 接受 `keyword`（name LIKE，两侧 trim、空串视为不筛）/ `category` / `sort`（白名单 latest/price_asc/price_desc，非法值回落 latest）/ `page` / `size`（默认 1/12，size 钳制 ≤50），只查 `status=1`，返回 `PageResult`，验证：测试库造 15 个商品，`size=10` 首页返回 10 条且 `total=15`；`keyword=苹果` 只回名称含"苹果"的商品；`sort=price_asc` 首条为最便宜
- [x] 2.3 新增 `GET /api/products/categories`：在售商品 `DISTINCT category` 升序，验证：与库内 `SELECT DISTINCT` 一致；下架商品所属分类若再无在售商品则不出现
- [x] 2.4 管理端商品增改接受 `category`（空则默认"其他"）与 `description`（≤500）；新增 `GET /api/products/admin-list?keyword=&status=&page=&size=`（ADMIN，含下架商品），验证：`user1` 请求 403；造一个 `status=0` 商品后 admin-list 能看到而 `/api/products` 看不到
- [x] 2.5 新建 `ProductSearchApiTest` 覆盖上述场景 + 逻辑删除商品不出现在任何列表，验证：`mvn test` 该类全绿

## 3. 订单详情与补全（后端）

- [x] 3.1 新建 `dto/OrderVO`（订单全字段 + `productName/productImage/groupBuyTitle/leaderName/pickupPointName/pickupPointAddress`）；`OrderService.enrich(List<Order>)` 批量补全（商品/团购各一次 `selectBatchIds`，团长一次，查不到的字段留空），验证：单测断言 3 笔订单补全只发生 3 次批量查询（代码审查级验证 + 集成断言字段值正确）
- [x] 3.2 `GET /api/orders/mine` 与 `GET /api/group-buys/{id}/orders` 返回补全后的 `OrderVO`，验证：用例断言 `mine` 首单含正确 `productName` 与 `pickupPointName`
- [x] 3.3 新增 `GET /api/orders/{id}`：本人可查；他人订单 400「订单不存在」；管理员可查任意订单，验证：三个角色三断言
- [x] 3.4 新增 `GET /api/admin/orders?status=&keyword=&page=&size=`（ADMIN）：keyword 匹配订单号或买家用户名，返回 `PageResult<OrderVO>`，验证：造 3 笔订单后按订单号搜只回 1 条、按买家用户名搜回该买家全部、非 ADMIN 403
- [x] 3.5 新建 `OrderDetailApiTest` 覆盖以上 + 已删除商品的订单 `productName` 为空不报错，验证：全绿

## 4. 个人中心（后端）

- [x] 4.1 新建 `dto/ChangePasswordRequest`（oldPassword/newPassword，6–32 校验）；`POST /api/auth/change-password`：旧密码不匹配 400、新旧相同 400、成功后新密码可登录旧密码不可登录，验证：三个用例
- [x] 4.2 新建 `dto/ProfileUpdateRequest`；`PUT /api/auth/profile`：仅 LEADER 的 `pickupPointName/pickupAddress` 生效（USER 传入提示不支持），名称非空校验，验证：团长改后团购详情的提货点即时为新值
- [x] 4.3 新建 `ProfileApiTest`，验证：全绿

## 5. 修复清单（后端，逐项回归测试）

- [x] 5.1 `FileController`：无扩展名 / null 文件名 → 400 而非 500；扩展名白名单 jpg/jpeg/png/gif/webp + contentType 须 image/*，非图片 400，验证：multipart 用例传 `.txt` 与传假扩展名真文本均被拒，传真 PNG 成功
- [x] 5.2 `GET /api/auth/me`：已删除（逻辑删除）用户 → 401 而非 500，验证：直接 UPDATE deleted=1 后调 me 断言 401
- [x] 5.3 `SecurityConfig` 统一过滤层 401/403 JSON（entryPoint / accessDeniedHandler），验证：无 token 访问 `/api/products` 断言 HTTP 401 + body code=401；无权限角色访问断言 403 + code=403
- [x] 5.4 `GlobalExceptionHandler` 500 兜底改固定文案 + log.error 堆栈，验证：响应 msg 不含异常类名/SQL 片段
- [x] 5.5 `GET /api/group-buys` 口碑标签批量查询（`ReviewAnalysisService.topTagsBatch`），消除逐商品 N+1，验证：行为等价（列表页 insightTags 与改前一致），`mvn test` 全量回归
- [x] 5.6 `OrderService.generateOrderNo` 加宽随机段（毫秒时间戳 + 6 位随机），验证：代码审查 + 既有下单用例回归

## 6. 管理端与个人中心（前端）

- [x] 6.1 `AdminProducts.vue` 重做：表单校验规则（名称必填≤128 / 价格>0 / 库存≥0 整数 / 分类必选 / 描述≤500）、图片上传接 `api/upload`（el-upload + 预览）、上下架 el-switch、v-loading、EmptyState、分页（admin-list 接口），验证：浏览器实测空名称提交被拦；上传图片后表单回填 URL；下架商品从 `/api/products` 消失
- [x] 6.2 新建 `AdminOrders.vue`（/admin/orders）：状态筛选 + 订单号/买家搜索 + 分页表格 + 退款按钮（确认弹窗 → `refundOrder` → 刷新），终态订单按钮禁用，验证：实测退款一笔待成团订单后状态变已退款、库存回补
- [x] 6.3 新建 `Profile.vue`（/profile）：账号信息卡（用户名/角色/注册时间）+ 修改密码表单（旧/新/确认，校验后调接口）+ 团长提货点卡（仅 LEADER），验证：user1 改密后退出用新密码登录成功；leader1 改提货点后开团详情页显示新地址
- [x] 6.4 `Dashboard.vue` 菜单：ADMIN 组加「订单管理」，所有角色加「个人中心」（我的组）；用户卡点击进 /profile，验证：三个角色分别登录菜单项正确

## 7. 商品与订单页（前端）

- [x] 7.1 `ProductList.vue` 重做：搜索框（与顶栏 `?keyword=` 双向同步，回车触发服务端检索）、分类 chips（categories 接口 + 全部）、排序 segmented（最新/价格升/价格降）、el-pagination、卡片视觉对齐 tokens 体系（占位渐变 + 分类标签 + 价格），SkeletonList / EmptyState，验证：搜索「苹果」只出苹果类目商品；切分类、切排序、翻页均触发请求且 URL query 同步
- [x] 7.2 `ProductDetail.vue` 重做：视觉对齐（面包屑、大图区、价格卡、分类/描述/库存信息、评分与口碑标签、评价列表），验证：详情含 description 文案；返回列表保留检索条件
- [x] 7.3 新建 `OrderDetail.vue`（/user/orders/:id）：状态卡（StatusBadge + 状态说明）、取货码票据（成团后）、商品/团购/团长/提货点信息区、操作（支付/取消/评价弹窗复用 UserOrders 逻辑）、加载骨架与不存在兜底，验证：从我的订单点卡片进入，四块信息正确；待支付单可支付
- [x] 7.4 `UserOrders.vue`：删掉全量商品映射与 `listProducts` 依赖，OrderCard 直接吃 VO 字段（`productTitle` / `pickupPointName` props 由 `o.productName` / `o.pickupPointName` 传入）；卡片加"查看详情"入口，验证：商品被删后订单列表仍显示占位而非空白
- [x] 7.5 `OrderCard.vue`：`productTitle` / `pickupPointName` 改为可选 props 由订单 VO 直接提供（保持组件接口兼容），验证：列表与详情两处渲染一致
- [x] 7.6 `UserGroups.vue`：右侧「按截止时间排序」假控件改为真实排序（综合/截止时间切换，客户端排序即可，数据集为进行中团），验证：切换后列表顺序变化且刷新后保持

## 8. 横切（前端）

- [x] 8.1 `router/index.js`：全部路由补 `name`/`meta.title`/`meta.roles`；守卫做角色校验（不符 → /dashboard + 警告）；加 404 兜底；已登录访问 /login|/register 重定向 /dashboard，验证：user1 手输 /admin/dashboard 被弹回；乱路径见 404 页
- [x] 8.2 新建 `NotFound.vue`（404 友好页，返回首页按钮），验证：路由可达
- [x] 8.3 `Register.vue` 视觉对齐 Login（渐变背景 / tokens / 卡片），功能不动，验证：注册流程回归通过
- [x] 8.4 删除 `ProductList.vue` 旧实现残留（空 watch 等，随 7.1 重写自然消除），验证：`grep -r "watch(() => route.query.keyword" 无命中

## 9. 验证与文档

- [x] 9.1 `mvn test` 全量通过（存量 67 + 新增 ≥25），验证：输出无 FAIL/ERROR
- [x] 9.2 `npm run build` 通过，验证：退出码 0
- [x] 9.3 端到端实测：重建库（schema+seed）→ 起后端 → 按验证报告方法学对新增接口逐个断言（商品检索/分页/排序、订单详情、改密、管理端订单、上传白名单、401 统一），结果追加 `docs/verification-report.md`，验证：报告中每条断言附实际响应
- [x] 9.4 同步 `README.md`（功能概览、接口一览 +8、测试数、db 目录说明、开发说明）与 `docs/implementation-inventory.md`，验证：接口表与实测一一对应
- [x] 9.5 归档本变更（delta 合并进基线，`openspec validate --specs` 通过），验证：基线 specs 含本轮新增 Requirement
