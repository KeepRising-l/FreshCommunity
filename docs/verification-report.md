# 规格符合性验证报告

- **首轮日期**：2026-10-10（MVP 基线 7 个 capability）
- **最新更新**：2026-10-17（新增 §六 AI 导购端到端实测）
- **范围**：`openspec/specs/` 基线的 Requirement / Scenario；§六 另覆盖 `ai-shopping-assistant` 变更的端到端路径
- **方法**：把规格场景写成可执行断言，对真实运行的后端（Spring Boot 8080 + MySQL `freshcommunity`）发请求实测，而非静态阅读代码
- **首轮结果**：**48 项断言，45 通过 / 3 未通过**（问题已在 2026-10-11 全部修复，见 §五）

> 结论：核心闭环（开团 → 参团 → 成团 → 核销）确实可用；但 `tasks.md` 中 22 个任务虽全部勾选，**不等于验证通过**——本次实测即发现 2 个勾选项与实际行为不符。

---

## 一、未通过项

### 1. 注册功能未实现（任务 2.1 已勾选，实为虚报）

- `tasks.md` 2.1 原文：「实现**注册**/登录接口与 JWT 签发」
- `specs/user-roles/spec.md` 首个 Requirement 即「角色**注册**与登录」
- **实际**：`AuthController` 只有 `POST /api/auth/login` 与 `GET /api/auth/me`，不存在 `/api/auth/register`（请求该路径落到兜底异常处理器，返回 HTTP 500）；前端 `router/index.js` 只有 `/login`，无注册页。三个测试账号由 `db/schema.sql` 预置，用户无法自助注册。
- **修复方向**：新增注册接口（校验用户名唯一、BCrypt 存储、默认 USER 角色）+ 前端注册页；或修改规格与任务描述，明确"仅预置账号、不支持自助注册"。

### 2. 取消订单不回退团购人数（真实缺陷）

- `OrderService.cancelOrder` 只做两件事：把订单置为 `CANCELLED`、回补库存。**不递减 `group_buy.member_count`**。
- 全代码库中 `member_count` 仅在支付成功时 `+1`（`GroupBuyMapper.incrementMemberCount`），**没有任何地方 `-1`**。
- **影响**：成团判定 `member_count >= target_count` 会被已取消订单虚高的人数顶过去，产生「虚假成团」。
  推演（`targetCount = 2`）：A 支付（mc=1）→ A 取消（mc 仍为 1）→ B 支付（mc=2 ≥ 2）→ **判定成团**，但该团实际只剩 1 笔有效订单。
- **实测证据**：支付后 `member_count=1`，取消该订单后 `member_count` 仍为 `1`。
- **修复方向**：`cancelOrder` 中对处于 `PENDING_GROUP`（已计入人数）的订单递减 `member_count`；注意仅在确实计入过人数时递减，避免对 `PENDING_PAYMENT` 订单误减。

### 3. 管理员退款无状态守卫，可无限刷库存（真实缺陷）

- `OrderService.refundOrderByAdmin` 执行 `updateStatus(orderId, "REFUNDED", order.getStatus())`——**把订单的当前状态当作预期状态传入**，条件恒成立，等于没有状态守卫；随后无条件 `restoreStock`。
- **影响**：对同一笔订单反复调用退款接口，每次都成功并再次回补库存，库存可被任意刷高。
- **实测证据**（订单数量 2 件）：

  | 操作 | 返回 | 商品库存 |
  | --- | --- | --- |
  | 初始 | — | 97 |
  | 第 1 次退款 | `code 200` | 99 |
  | 第 2 次退款 | `code 200` | 101 |
  | 第 3 次退款 | `code 200` | 103 |

  净增 **6**，正确值应为 **2**。
- **修复方向**：退款前校验当前状态不属于终态（至少拒绝 `REFUNDED`，另需明确 `COMPLETED` 已提货订单能否退款），并保证 `restoreStock` 仅在首次退款时执行。

---

## 二、次要问题（不影响主流程）

| # | 问题 | 位置 | 说明 |
| --- | --- | --- | --- |
| 1 | 密码错误返回 HTTP 200 + body `code:401` | `AuthController.login` | 任务 2.1 字面要求「返回 401」，实为响应体 code=401；前端按 body 判断故未暴露 |
| 2 | 未知路径返回 500 而非 404 | `GlobalExceptionHandler` | 兜底 `Exception` 处理器吞掉了 `NoResourceFoundException` |
| 3 | 取货码无唯一性保证 | `OrderService.generatePickupCode` | 规格明写「**唯一**的 6 位数字取货码」，实现为 `new Random()` 无查重；10⁶ 空间下小规模不会碰撞，但未满足规格；一旦碰撞，`findByPickupCode` 返回单对象会抛 `TooManyResultsException` |
| 4 | 上传接口无角色限制 | `FileController.upload` | 无 `@PreAuthorize`，任何登录用户均可上传文件（README 已如实标注） |
| 5 | 变更未归档 | `openspec/` | `tasks.md` 末尾的 Workflow follow-up（归档并同步主 specs）未执行；`openspec/specs/` 与 `changes/archive/` 仅有 `.gitkeep` |

---

## 三、已验证通过的项（45 项）

覆盖情况：

- **认证与权限（user-roles）**：三角色登录、错误密码拒绝、用户访问管理员接口 403、无 token 403、团长「我的团购」只返回本人团购、团购详情含团长提货点名称与地址
- **商品（product-catalog）**：管理员增删改、缺必填字段返回 400、改价立即生效
- **团购创建（group-buying）**：截止时间早于当前被拒且未落库、成团人数 0 被拒、用户开团 403、创建后状态 ONGOING
- **库存（inventory）**：**库存=1 时 8 个并发下单仅 1 单成功**、库存不为负、超量下单被拒、取消回补
- **订单生命周期（order-lifecycle）**：支付后转 `PENDING_GROUP` 并计入人数、待成团可取消、已成团不可取消、成团后停止下单、取货码为 6 位
- **核销（pickup-verification）**：无效码被拒、有效码核销转 `COMPLETED`、重复核销被拒
- **定时任务**：待支付超 30 分钟自动关单并回补库存且不计入人数；到点不达标解散 → 团购 `FAIL`、订单 `REFUNDED`、库存回补、截止后补单被拒
- **看板（admin-dashboard）**：今日订单数、今日成团数与数据库直查结果一致

---

## 四、复现方式

验证脚本未纳入仓库（一次性工具）。复现要点：

1. `mysql -uroot -p < db/schema.sql && mysql -uroot -p < db/seed.sql`
2. `cd backend && DB_PASSWORD=<密码> mvn spring-boot:run`
3. 用三个测试账号（密码 `123456`）按上述场景发请求；定时任务相关用例需等待最多 60 秒的任务周期，或直接改库把 `deadline` / `created_at` 回拨后再等一轮。

> 验证过程中产生的数据已清理，数据库已还原为 `schema.sql` + `seed.sql` 的干净演示状态。

---

## 五、修复记录（2026-10-11）

针对上述问题已完成修复，并对修复项做真实运行验证（后端 8080 + MySQL，12 项断言全通过）。

| 原问题 | 修复内容 | 涉及文件 | 运行验证 |
| --- | --- | --- | --- |
| 注册功能未实现 | 新增 `POST /api/auth/register`：用户名唯一校验、BCrypt、默认 USER（可选 LEADER 需提货点）、拒绝 ADMIN 自助注册、注册即发 JWT | `AuthController`、`RegisterRequest`（新增） | 注册 200、重复名 400、注册 ADMIN 400、团长缺提货点 400 |
| 取消订单不回退人数 | `cancelOrder` 对 `PENDING_GROUP` 订单递减 `member_count` | `OrderService`、`GroupBuyMapper` | A 支付→A 取消→B 支付，团购仍 ONGOING（不再虚假成团） |
| 管理员退款无状态守卫 | 终态订单拒绝退款，`restoreStock` 仅首次执行；退款 `PENDING_GROUP` 订单同步回退人数 | `OrderService` | 首次退款 200 且回补库存，重复退款 400 且库存不再增长 |
| 登录失败返回 HTTP 200+body 401 | 抛出 `UnauthorizedException`，由全局处理器返回 HTTP 401 | `AuthController`、`UnauthorizedException`（新增）、`GlobalExceptionHandler` | 错误密码返回 HTTP 401 |
| 未知路径返回 500 | 新增 `NoResourceFoundException`/`NoHandlerFoundException` 处理器返回 404，方法不支持返回 405 | `GlobalExceptionHandler` | `/api/auth/nope`→404；GET 登录接口→405 |
| 取货码无唯一性保证 | 生成时查重（最多重试 20 次），改用 `SecureRandom` | `OrderService`、`OrderMapper` | 批量成团取货码互不重复 |
| 上传接口无角色限制 | `upload` 加 `@PreAuthorize("hasRole('ADMIN')")` | `FileController` | 普通用户 403、管理员 200 |

前端同步：新增 `Register.vue`、`/register` 路由与守卫放行、登录页注册入口、`api/auth.js` 的 `register`、401 跳转时排除登录/注册页。

> 本次修复后数据库再次还原为 `schema.sql` + `seed.sql` 的干净演示状态。变更归档已于 2026-10-11 完成（见 `openspec/changes/archive/2026-10-11-mvp-group-buying/`）。

---

## 六、AI 导购端到端实测（2026-10-17）

- **范围**：`ai-shopping-assistant` 变更的端到端演示路径（任务 5.1），以及实施期间暴露缺陷的修复验证
- **方法**：启动真实后端（Spring Boot 8080 + MySQL `freshcommunity`）与真实前端构建产物，用 HTTP 请求按演示脚本逐步执行，每步读取数据库/接口返回值核对；**不读取代码下结论**
- **环境**：`db/schema.sql` + `db/seed.sql` 重建后的干净演示库

### 6.1 演示路径（无 key / 规则解析）

| 步骤 | 操作 | 实测结果 |
| --- | --- | --- |
| 1 | `user1` 调 `POST /api/subscriptions/parse`，文本「想要 10 元以内的苹果，有货叫我」 | `{keyword: 苹果, maxPrice: 10}` |
| 2 | 确认创建订阅 | 200，返回 id=1、`matchedCount=0`（此时无进行中苹果团） |
| 3 | `leader1` 开团：商品「红富士苹果」、拼团价 8.8、成团人数 5 | 200，团购 id=13；列表显示拼团价 8.8 / 原价 12.9 |
| 4 | 查 `user1` 消息中心 | 收到 `SUBSCRIPTION_MATCH`：「「红富士苹果」正在团购：拼团价 ¥8.80，还差 5 人成团，去看看吧。已有 3 人评价都说实惠、新鲜、甜。」 |
| 5 | `user1` 下单 2 件并支付 | 订单金额 17.60（= 8.8 × 2），状态 `PENDING_PAYMENT` → `PENDING_GROUP` |

### 6.2 演示路径（有 key / LLM）

- 用本地 OpenAI 兼容 mock 端点（`AI_BASE_URL=http://localhost:18080`）验证 `AiClient` 链路，非真实供应商。
- 解析结果 `{keyword: 红富士苹果, maxPrice: 10}`——口语「苹果」被归一为候选商品名，证明商品名注入生效。
- 评价洞察改走 LLM：8 条历史评价全部按 mock 返回的 `POSITIVE / 新鲜,实惠` 落库。
- 开团后提醒：「……已有 3 人评价都说新鲜、实惠。」
- ⚠️ **真实供应商密钥未验证**（本机无密钥），部署时需补一次真机确认。

### 6.3 降级路径（key 已配但端点不可达）

- `AI_BASE_URL` 指向未监听端口，启动正常（`AiClient` 不因连接失败阻塞启动）。
- `POST /api/subscriptions/parse` 返回 200 + `{keyword: 苹果, maxPrice: 10}`，耗时 0.11s（连接被拒即时回落，未走满 5 秒超时）。

### 6.4 本次实测发现并修复的缺陷

| # | 问题 | 影响 | 修复 | 回归用例 |
| --- | --- | --- | --- | --- |
| 1 | 开发库缺 `review_analysis` 表（旧库未重建） | 商品详情 500；定时任务每 60 秒刷 `Table doesn't exist` | 重跑 `schema.sql`；README 补充「已有旧库必须重跑」说明 | — （环境问题） |
| 2 | 演示脚本的「苹果」在种子数据中不存在 | 订阅「10 元以内的苹果」永远无团可命中，演示走不通 | `seed.sql` 新增商品「红富士苹果」+ 历史苹果团/订单/评价 | 端到端实测 |
| 3 | 开团无法指定拼团价（写死零售价） | 演示脚本「开 8.8 元苹果团」无法复现；拼团价永远等于零售价 | `GroupBuyCreateRequest.price` + `resolvePrice` 校验；团长端表单增加拼团价 | `createGroupBuyWithCustomPrice` / `customPriceValidation` |
| 4 | 中性评价被写入「中性」标签 | 商品详情显示「中性」，提醒写成「都说中性、新鲜」 | 情感倾向不再作为标签写入 | `neutralReviewProducesNoTags` |
| 5 | 规则词典单字误伤（「快」→「到货快」） | 「一箱很快吃完了」被误判为正面并产出「到货快」 | 词典改为长词优先、去掉单字泛匹配 | `keywordDictionaryMatchesLongestFirst` |
| 6 | 否定表达被记成正面（「不新鲜」命中正向词「新鲜」） | 差评可能同时出现正面标签，情感与标签自相矛盾 | 正向词命中后检查紧邻否定前缀（不 / 没 / 没有 / 不太 / 不够 / 不怎么） | `negatedPositiveWordIsNotPositive` |
| 7 | 测试期定时任务与测试库重建抢跑 | `mvn test` 日志刷假错误，干扰「全绿」判断 | `scheduling.initial-delay-ms`，测试 profile 置 1 小时 | `mvn test` 输出无该错误 |

### 6.5 回归结果

- `cd backend && DB_PASSWORD=1234 mvn test`：**52 用例全绿**（原 47 + 本次新增 5），无回归失败
- `cd frontend && npm run build`：通过，主包 1.27MB
- `openspec validate --all`：11/11 通过
- 实测结束后数据库已还原为 `schema.sql` + `seed.sql` 干净演示状态

---

## 七、看板与团长端实测（2026-10-23）

> 环境：后端 `localhost:8080`（dev 库 `freshcommunity`）＋ 前端 `localhost:5173`；账号 `admin` / `leader1` / `user1`，密码均为 `123456`。
> 证据形式：对运行中的页面执行 DOM 断言，并与同一时刻的接口响应逐项比对。本轮 IDE 内置浏览器无法提供可视区域（截图返回 `viewport=0x0`），因此**未附截图**，证据为 DOM 结构与接口响应原文。

### 7.1 管理看板（`/admin/dashboard`）

前置数据：通过真实接口新建团购 13（target=2，拼团价 12.50）与团购 14（target=1，拼团价 8.00），`user1` 分别下单支付（订单 31 / 32，均 1 件）。

| # | 断言 | 实测 | 结论 |
| --- | --- | --- | --- |
| 1 | 5 张 KPI 卡与接口一致 | 页面 `2 / 1 / ¥20.50 / 16 / 1`；接口 `todayOrders=2, todaySuccessGroups=1, todayGmv=20.5, totalUsers=16, ongoingGroupBuys=1` | ✅ |
| 2 | 成交额只计已支付口径 | 今日两笔已支付 12.50 + 8.00 = 20.50，与 `todayGmv` 一致；`PENDING_GROUP` 订单（31）计入，未支付/取消/退款不计（口径另由 `DashboardApiTest` 覆盖） | ✅ |
| 3 | 状态分布与库内分组一致 | 累计 32 单：待成团 1 / 已成团 5 / 已完成 12 / 已取消 4 / 已退款 10；条宽 `3.1% / 15.6% / 37.5% / 12.5% / 31.3%`（= 计数/32） | ✅ |
| 4 | 趋势 7 点连续 | `10-17 ¥388.10 / 15 单`、`10-23 ¥20.50 / 2 单`，中间 5 日 `¥0.00 / 0 单`，与 `trend7d` 逐项一致 | ✅ |
| 5 | 窄容器不挤叠 | 容器压到 460px：SVG 由 510px 缩至 419px，坐标轴 `scrollWidth == clientWidth`（419），7 个标签无省略截断 | ✅ |
| 6 | 无订单时空态 | 后端临时切到空库 `freshcommunity_test`：5 张卡全 0、状态分布显示「还没有订单」空态、条数 0、「累计 0 单」，页面照常渲染 | ✅ |
| 7 | 不引入图表库 | `frontend/package.json` / `package-lock.json` 无改动；`npm run build` 通过 | ✅ |

### 7.2 团长端（`/leader/groups`、`/leader/verify`）

| # | 断言 | 实测 | 结论 |
| --- | --- | --- | --- |
| 1 | 概览四卡（核销前） | 进行中 1 / 待取货 1 / 已核销 10 / 累计成交额 ¥211.20（= 190.70 + 12.50 + 8.00） | ✅ |
| 2 | 概览四卡（核销后） | 进行中 1 / 待取货 0 / 已核销 11 / 累计成交额 ¥211.20（成交额不因核销变化） | ✅ |
| 3 | 还差几人 | 进行中（2 人团、已支付 1）显示「1 人」；已成团显示「已达标」；已解散显示「—」 | ✅ |
| 4 | 核销进度 | 已成团团购核销前「0/1」、核销后「1/1」；进行中与已解散显示「—」 | ✅ |
| 5 | 按钮门槛 | 输入不足 6 位时「确认核销」为禁用态；输入 6 位后启用 | ✅ |
| 6 | 成功反馈 | 输入 `200894` → 结果条 `is-ok`「核销成功」；输入清空、焦点回到输入框、记录列表自动刷新且该单在首位 | ✅ |
| 7 | 失败反馈 | 输入 `000000` → 结果条 `is-bad`「取货码无效」；**输入内容保留**（仍为 `000000`）、焦点保留 | ✅ |
| 8 | 仅限数字 | 输入 `12ab34` 被过滤为 `1234` | ✅ |
| 9 | 最近核销记录 | 最多 10 条、按核销时间倒序（首条 `E2E 团购 · 立即成团 / O179…324 / 1 件 · ¥8.00 / 2026-10-23 21:09`），响应不含买家身份字段 | ✅ |

### 7.3 端到端链路

| 步骤 | 操作 | 实测结果 |
| --- | --- | --- |
| 1 | `leader1` 开团：target=2、拼团价 12.50 | 200，团购 id=13 |
| 2 | `user1` 下单并支付 | 订单 31，`PENDING_GROUP`，团购 1/2 |
| 3 | 查看团长页 | 进行中 1、还差 1 人、待取货 0、成交额 ¥211.20 |
| 4 | `leader1` 开团 target=1、拼团价 8.00；`user1` 下单支付 | 团购 14 立即成团；订单 32 `SUCCESS`，取货码 `200894` |
| 5 | 查看团长页 | 待取货 1、该团核销进度 0/1 |
| 6 | 核销台输入 `200894` 核销 | 成功；待取货 0、已核销 11、进度 1/1 |
| 7 | 查看管理看板 | 今日订单 2、今日成团 1、今日成交额 ¥20.50、进行中团购 1；趋势 10-23 = ¥20.50 / 2 单 |

### 7.4 本轮发现并修复的缺陷

| # | 问题 | 影响 | 修复 | 回归用例 |
| --- | --- | --- | --- | --- |
| 1 | 团长概览的「待取货」只统计 `PENDING_PICKUP`，但成团链路写入的是 `SUCCESS`（带取货码），代码中从未产生 `PENDING_PICKUP` | 真实链路上「待取货订单」恒为 0；原用例因直接插入 `PENDING_PICKUP` 而**假通过** | `OrderMapper.countPendingPickupByLeader` 改为统计 `status IN ('SUCCESS','PENDING_PICKUP')` | `summaryFollowsRealGroupSuccessFlow`（真实开团→下单→支付→核销）、`summaryCountsOnlyOwnData`（改用真实状态） |
| 2 | 看板既有 `countTodayOrders` 未带 `deleted = 0`（手写 `@Select` 不享受 MyBatis-Plus 逻辑删除注入） | 软删订单仍被计入今日订单数 | 补 `deleted = 0`（含新增三条聚合 SQL） | `deletedOrdersExcluded` |

### 7.5 回归结果

- `cd backend && DB_PASSWORD=1234 mvn test`：**67 用例全绿**（52 基线 + `DashboardApiTest` 7 + `LeaderConsoleApiTest` 8）
- `cd frontend && npm run build`：通过；主包 1.27MB，依赖未变
- 本轮 E2E 在开发库留下团购 13/14、订单 31/32 作为演示数据；需要干净状态时重跑 `db/schema.sql` + `db/seed.sql`

---

## 八、目录检索·订单详情·个人中心·管理端实测（2026-10-25）

- **变更**：`2026-10-25-catalog-retrieval-user-center-admin`（商品检索分页 / 订单详情与信息补全 / 修改密码与提货点维护 / 管理端订单列表与商品管理改造 / 平台修复清单）
- **方法**：与 §四 相同——重建演示库（`schema.sql` + `seed.sql`，17 商品 / 6 分类 / 30 订单）→ 启动后端 → 对真实 HTTP 接口逐项断言（脚本为一次性工具，未入库）
- **环境**：Spring Boot 8080 + MySQL `freshcommunity`，2026-10-25 实测

### 8.1 商品检索（product-catalog）

| # | 断言 | 实测结果 |
| --- | --- | --- |
| 1 | `GET /api/products?keyword=苹果` 只返回名称含「苹果」的在售商品 | ✅ total=1，红富士苹果 |
| 2 | `?category=水果` 只返回该分类 | ✅ total=6，全部 category=水果 |
| 3 | `?sort=price_asc` 首条为全场最低价 | ✅ 山东大葱 ¥5.50，且首条 ≤ 次条 |
| 4 | `?page=2&size=5` 分页结构正确 | ✅ records=5、total=17、pages=4 |
| 5 | `GET /api/products/categories` 与库内 DISTINCT 一致且升序 | ✅ 6 类升序 |
| 6 | `GET /api/products/admin-list` 管理员可见全量 17 件 | ✅ total=17 |
| 7 | 普通用户访问 admin-list | ✅ HTTP 403 + 统一 JSON（code=403） |

### 8.2 订单详情与信息补全（order-lifecycle）

| # | 断言 | 实测结果 |
| --- | --- | --- |
| 8 | `/api/orders/mine` 每单自带商品名与提货点 | ✅ D0001 → 红颜草莓 / 幸福小区便利店 / 红颜草莓 · 现摘现发 |
| 9 | 本人查 `/api/orders/{id}` 含团长与提货点地址 | ✅ leader1 / 幸福小区东门 101 号 |
| 10 | 他人订单 | ✅ HTTP 400「订单不存在」（不区分 404/403 防探测） |
| 11 | 管理员查任意订单 | ✅ 200，orderNo=D0001 |
| 12 | `/api/admin/orders?keyword=订单号` | ✅ total=1，含 buyerName=user1 与商品名 |
| 13 | `?keyword=user1` 按买家搜索 | ✅ total=5（D0001/D0006/D0010/D0025/D0028） |
| 14 | `?status=REFUNDED` 状态筛选 | ✅ total=5 且全部 REFUNDED |
| 15 | 非管理员访问管理订单列表 | ✅ 403 |

### 8.3 个人中心（user-roles）

| # | 断言 | 实测结果 |
| --- | --- | --- |
| 16 | change-password 成功后旧密码登录 401、新密码 200 | ✅ |
| 17 | 旧密码错误被拒 | ✅ 400「旧密码错误」 |
| 18 | 团长改提货点后团购详情即时生效 | ✅ 改「E2E 临时提货点」→ 团购 4 详情同步；断言后已复原 |
| 19 | 普通用户维护提货点被拒 | ✅ 400「仅团长可维护提货点信息」 |
| 20 | `/api/auth/me` 返回注册时间 | ✅ 含 createdAt 字段 |

### 8.4 上传加固与统一 401/403（修复回归）

| # | 断言 | 实测结果 |
| --- | --- | --- |
| 21 | 上传 `.txt` | ✅ 400（旧实现会 500/落盘任意文件） |
| 22 | 上传无扩展名文件 | ✅ 400（旧实现 `substring(-1)` 越界崩溃） |
| 23 | 假 `.png` 扩展名 + text/plain 类型 | ✅ 400（双重校验生效） |
| 24 | 合法 PNG | ✅ 200，返回 `/uploads/…` 路径 |
| 25 | 无 token 访问受保护接口 | ✅ HTTP 401 + `{code:401,msg:未登录或登录已过期}`（旧为空体 403） |
| 26 | 坏 token | ✅ 同样统一 401 |
| 27 | 已登录无权限 | ✅ HTTP 403 + `{code:403}` |

### 8.5 回归

- `GET /api/group-buys` 列表结构正常，口碑标签批量查询（`topTagsBatch`）替代逐商品 N+1 后行为等价：每条记录均含 `insightTags` 列表字段 ✅
- 集成测试：`DB_PASSWORD=1234 mvn test` → **89 用例全绿**（67 基线 + `ProductSearchApiTest` 7 + `OrderDetailApiTest` 5 + `ProfileApiTest` 5 + `UploadAndSecurityApiTest` 5）
- 前端 `npm run build` 通过，依赖未变
- E2E 临时账号 `e2e_tmp_user` 已从演示库清除，提货点改动已复原，种子数据保持纯净
