# 社区生鲜团购平台（FreshCommunity）

毕业设计项目。围绕社区团购的核心闭环 —— **团长开团 → 邻居参团 → 达标成团 → 提货点自提核销** —— 跑通用户、团长、管理员三种角色的完整业务流程。

后端 Spring Boot 3 + MyBatis-Plus + JWT，前端 Vue 3 + Element Plus，数据库 MySQL。

---

## 功能概览

| 角色 | 能做什么 |
| --- | --- |
| 用户 | 浏览商品、参加团购下单、模拟支付、取消未成团订单、查看我的订单与取货码 |
| 团长 | 发起团购（设成团人数 + 截止时间）、查看自己开的团、输入取货码核销 |
| 管理员 | 商品增删改查（名称/价格/库存/图片）、数据看板、订单退款兜底 |

**核心机制**

- **团购生命周期**：团长设成团人数与截止时间 → 用户下单（模拟支付）→ 人数达标**提前成团**；到点未达标**自动解散并退款**
- **库存防超卖**：下单时用 `UPDATE product SET stock = stock - #{qty} WHERE id = ? AND stock >= #{qty}` 原子扣减，受影响行数为 0 即库存不足；取消/解散时回补
- **订单状态机**：待支付 → 待成团 → 已成团 → 待取货 → 已完成／已取消／已退款
- **取货码核销**：成团后为每笔订单生成 6 位取货码，团长网页输码核销，仅限本团订单且防重复核销
- **定时任务**（每 60 秒）：
  - 处理到期团购（达标成团 / 未达标解散退款）
  - 关闭超过 30 分钟未支付的订单并回补库存

**明确不做**（MVP 边界）：商家入驻、配送骑手、佣金结算、真实支付、限购/优惠券/秒杀、评价、消息推送、App/小程序。支付为 `MockPayServiceImpl` 模拟实现，通过 `PayService` 接口预留了接真实支付的扩展点。

---

## 技术栈

**后端**

- Spring Boot 3.2.5 / Java 17
- Spring Security + JWT（jjwt 0.12.5），无状态鉴权
- MyBatis-Plus 3.5.5（逻辑删除、自动填充）
- MySQL 8+（`mysql-connector-j`）
- Lombok

**前端**

- Vue 3.4（Composition API）+ Vite 5
- Vue Router 4 + Pinia 2
- Element Plus 2.6
- Axios（统一封装于 `src/utils/request.js`，自动带 JWT、统一处理 401）

---

## 项目结构

```
FreshCommunity/
├── backend/                       # Spring Boot 后端
│   └── src/main/
│       ├── java/com/freshcommunity/
│       │   ├── controller/        # Auth / Product / GroupBuy / Order / Admin / File / Health
│       │   ├── service/           # OrderService / GroupBuyService / PayService(接口) + MockPayServiceImpl
│       │   ├── mapper/            # MyBatis-Plus Mapper（含原子扣减、超时关单等自定义 SQL）
│       │   ├── entity/            # User / Product / GroupBuy / GroupBuyItem / Order
│       │   ├── security/          # SecurityConfig / JwtUtil / JwtAuthenticationFilter
│       │   ├── job/               # ScheduledJobs 定时任务
│       │   ├── dto/  common/  config/
│       └── resources/
│           ├── application.yml        # 端口 8080、JWT、上传路径
│           └── application-dev.yml    # 数据源（dev profile）
├── frontend/                      # Vue 3 前端
│   └── src/
│       ├── views/                 # Login / Home / AdminProducts / AdminDashboard /
│       │                          # LeaderGroups / LeaderVerify / UserGroups / UserOrders
│       ├── api/                   # 按模块拆分的接口封装
│       ├── stores/user.js         # Pinia：token 与当前用户
│       ├── router/index.js        # 路由（未登录统一跳 /login）
│       └── utils/request.js       # Axios 实例
├── db/
│   ├── schema.sql                 # 建库建表 + 三个测试账号 + 3 个商品
│   └── seed.sql                   # 可选：一场进行中的演示团购
└── openspec/                      # 需求与设计文档（capability 规格、任务清单）
```

---

## 快速开始

### 环境要求

Java 17、Maven 3.8+、Node.js 18+、MySQL 8+

### 1. 建库并导入数据

```bash
mysql -uroot -p < db/schema.sql
mysql -uroot -p < db/seed.sql      # 可选：导入一场进行中的演示团购
```

`schema.sql` 会创建 `freshcommunity` 库、5 张表，并写入三个测试账号和 3 个商品。

### 2. 启动后端

数据库密码**不写在配置文件里**，通过环境变量传入（`application-dev.yml` 里是 `${DB_PASSWORD:root}`，不设则回退为 `root`）：

```bash
cd backend
DB_PASSWORD=你的MySQL密码 mvn spring-boot:run
```

Windows PowerShell：

```powershell
cd backend
$env:DB_PASSWORD="你的MySQL密码"; mvn spring-boot:run
```

IntelliJ IDEA 中：Run Configuration → Environment variables 里加 `DB_PASSWORD=你的MySQL密码`。

后端跑在 <http://localhost:8080>，健康检查 `GET /api/health` 应返回 `{"code":200,"msg":"success","data":"ok"}`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5173>。Vite 已把 `/api` 反向代理到 `http://localhost:8080`，无需额外配置跨域。

### 4. 登录

路由守卫要求先登录，未登录访问任何页面都会跳转到 `/login`。三个测试账号密码统一为 **`123456`**：

| 账号 | 角色 | 建议用途 |
| --- | --- | --- |
| `admin` | 管理员 | 商品管理、看板、退款 |
| `leader1` | 团长 | 开团、核销（已绑定提货点「幸福小区便利店」） |
| `user1` | 用户 | 浏览、参团下单、查看订单 |

> 演示闭环流程：`user1` 参团下单并支付 → 达到成团人数后自动成团并生成取货码 → `leader1` 在「核销」页输入取货码 → 订单变为已完成。

---

## 接口一览

统一返回 `{ code, msg, data }`，鉴权方式为 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/health` | 健康检查 | 公开 |
| POST | `/api/auth/login` | 登录，返回 JWT | 公开 |
| GET | `/api/auth/me` | 当前用户信息 | 登录 |
| GET | `/api/products` · `/api/products/{id}` | 商品列表 / 详情 | 登录 |
| POST/PUT/DELETE | `/api/products/**` | 商品增删改 | 管理员 |
| GET | `/api/group-buys` | 团购列表 | 登录 |
| GET | `/api/group-buys/mine` | 我发起的团购 | 团长 |
| GET | `/api/group-buys/{id}` | 团购详情（含团长提货点） | 登录 |
| POST | `/api/group-buys` | 发起团购 | 团长 |
| POST | `/api/orders?groupBuyId=&quantity=` | 下单（原子扣库存） | 登录 |
| POST | `/api/orders/{id}/pay` | 模拟支付 | 登录 |
| POST | `/api/orders/{id}/cancel` | 取消（仅未成团） | 登录 |
| GET | `/api/orders/mine` | 我的订单 | 登录 |
| POST | `/api/orders/verify?pickupCode=` | 取货码核销 | 团长 |
| POST | `/api/orders/{id}/refund` | 退款兜底 | 管理员 |
| GET | `/api/admin/dashboard` | 看板（今日订单数/成团数） | 管理员 |
| POST | `/api/upload` | 上传商品图片（≤5MB） | 登录 |

---

## 开发说明

- **数据库表名 `order` 是 MySQL 保留字**，实体上必须写成 `@TableName("`order`")`（带反引号），否则 MyBatis-Plus 生成的 `SELECT ... FROM order` 会报 `SQLSyntaxErrorException`。
- **JWT 无法主动失效**：token 签发后到期前一直有效。毕设场景可接受，生产需引入黑名单或改用短 token + refresh token。
- **密码存储**：BCrypt 哈希，`$2a$10$` 前缀，长度 60。手写 `schema.sql` 时注意别写坏哈希长度，否则登录会恒定失败。
- 商品图片上传到 `${user.dir}/uploads`，通过 `/uploads/**` 静态映射访问，该目录已在 `.gitignore` 中忽略。

---

## 需求文档

`openspec/` 目录下是完整的规格文档，含 7 个 capability 的详细场景定义与任务清单：

```
openspec/changes/mvp-group-buying/
├── proposal.md      # 为什么做、做什么、边界
├── design.md        # 表结构、状态机、关键决策与取舍
├── tasks.md         # 任务清单
└── specs/           # 各 capability 的 Requirement / Scenario
    ├── user-roles/  product-catalog/  group-buying/  inventory/
    └── order-lifecycle/  pickup-verification/  admin-dashboard/
```
