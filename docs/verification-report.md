# MVP 规格符合性验证报告

- **日期**：2026-10-10
- **范围**：`openspec/changes/mvp-group-buying/specs/` 下 7 个 capability 的 Requirement / Scenario
- **方法**：把规格场景写成可执行断言，对真实运行的后端（Spring Boot 8080 + MySQL `freshcommunity`）发请求实测，而非静态阅读代码
- **结果**：**48 项断言，45 通过 / 3 未通过**

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
