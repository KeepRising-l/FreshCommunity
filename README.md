# 社区生鲜团购平台（FreshCommunity）

毕业设计项目。围绕社区团购的核心闭环 —— **团长开团 → 邻居参团 → 达标成团 → 提货点自提核销** —— 跑通用户、团长、管理员三种角色的完整业务流程。

后端 Spring Boot 3 + MyBatis-Plus + JWT，前端 Vue 3 + Element Plus，数据库 MySQL。

---

## 功能概览

| 角色 | 能做什么 |
| --- | --- |
| 用户 | 自助注册/登录、修改密码、按关键词/分类/排序/分页检索商品、浏览商品详情与评分、参加团购下单、模拟支付、取消未成团订单、查看订单列表与订单详情（含取货码）、评价已完成订单、查看站内消息、AI 导购订阅 |
| 团长 | 发起团购（选商品 + 设拼团价 / 成团人数 / 截止时间）、查看自己开的团与经营概览、维护本人提货点、输入取货码核销并查看最近核销记录 |
| 管理员 | 商品增删改查（名称/分类/描述/价格/库存/图片上传/上下架开关）、全量订单管理（状态筛选 + 订单号/买家搜索 + 退款兜底入口）、数据看板（KPI + 订单状态分布 + 近 7 日趋势） |

**核心机制**

- **自助注册与登录**：用户/团长可自助注册（用户名唯一校验、BCrypt 存储、注册即签发 JWT）；管理员账号由初始化 SQL 预置，不开放自助注册
- **团购生命周期**：团长选商品、设拼团价（不填取零售价）、成团人数与截止时间 → 用户下单（模拟支付）→ 人数达标**提前成团**；到点未达标**自动解散并退款**
- **库存防超卖**：下单时用 `UPDATE product SET stock = stock - #{qty} WHERE id = ? AND stock >= #{qty}` 原子扣减，受影响行数为 0 即库存不足；取消/解散时回补
- **订单状态机**：待支付 → 待成团 → 已成团 → 待取货 → 已完成／已取消／已退款
- **取货码核销**：成团后为每笔订单生成 6 位取货码，团长网页输码核销，仅限本团订单且防重复核销
- **定时任务**（每 60 秒）：
  - 处理到期团购（达标成团 / 未达标解散退款）
  - 关闭超过 30 分钟未支付的订单并回补库存
- **评价**：已完成（已核销）订单可对**商品**与**团长**分别打分（1–5 星）+ 文字评论，一单一评（`review.order_id` 唯一索引防重复）；商品与团长各自汇总平均分与评价数，实时聚合
- **站内消息**：六类事件自动发站内信 —— 成团、解散退款、待支付超时关单、核销成功、管理员退款、需求订阅命中；与状态变更同事务写入，含未读计数与标记已读
- **AI 导购 · 即时推荐**：AI 导购页「问 AI」——一句话需求（如"帮我找性价比高的苹果"），系统把 41 个在售商品、进行中团购（拼团价/进度/剩余时间/提货点）与口碑标签喂给大模型，即时返回"首选推荐 + 备选 + 小贴士"的中文回答和可点击的商品卡片（LLM 不可用时明确报错、绝不伪造推荐；请求侧按 DeepSeek 标准参数关闭思考模式）；推荐卡片可**直接参团下单**（确认数量/提货点 → 按拼团价创建订单 → 引导支付，复用既有库存与订单状态机）
- **AI 导购 · 需求订阅**：用户用一句话订阅商品与价格意向（如「10 元以内的苹果」）；解析为结构化条件（关键词 + 价格上限）→ 用户确认后创建订阅 → 匹配已有团与新开团，命中即发站内信（含口碑摘要）。解析走「LLM 优先、规则兜底」：配置了 OpenAI 兼容密钥则调用大模型并把口语词归一映射到在售商品名，无密钥或调用失败时自动回落正则规则解析，因此**默认无需任何外部服务即可运行演示**
- **AI 导购 · 匹配可靠性**：三条匹配路径（订阅时立即匹配 / 开团触发 / 60 秒周期补偿扫描）统一经 `subscription_match` 表的 `(subscription_id, group_buy_id)` 唯一键闸门——`INSERT IGNORE` 首占成功才发提醒，并发"建订阅 × 开团"互相错过也会在一分钟内被补偿扫描补齐，且至多一条；命中提醒携带团购引用（`refGroupBuyId`），消息中心点击直达团购详情（订单类事件消息同样可跳订单详情）
- **AI 导购 · 评价洞察**：定时任务（每 60 秒）扫描有文字、尚无分析记录的评价，生成情感倾向（正面/负面/中性）与关键词标签，写入 `review_analysis`；商品详情与团购详情返回高频口碑标签（`insightTags`），订阅命中提醒附「已有 N 人评价都说…」摘要。同样「LLM 优先、规则词典兜底」

- **商品检索与分页**：商品带分类与描述属性；列表接口支持关键词（名称模糊）、分类、排序（最新/价格升降，白名单校验）与分页（`PageResult{records,total,current,size,pages}`），首次启用 MyBatis-Plus 分页拦截器；管理端另有含下架商品的全量列表
- **订单信息补全**：「我的订单」/ 订单详情 / 管理端订单列表的 VO 由后端批量补全商品名、商品图、团购标题、团长与提货点（批量查询，无 N+1），前端不再自行拉商品做映射
- **账号自助维护**：修改密码（旧密码校验 + 6-32 位 + 不得与旧密码相同）、团长维护提货点（即时生效于其名下团购）
- **真实图片**：商品与提货点种子数据自带真实照片（Wikimedia Commons 免费授权，随仓库内置离线可用，`scripts/fetch-seed-images.py` 可刷新）；emoji 占位降级为无图兜底
- **图片上传开放**：上传接口对所有登录用户可用（图片白名单 + 5MB 不变）——头像全角色可换、团长可传提货点照片、管理员维护商品图；个人中心上传头像后侧边栏/顶栏即时生效
- **移动端适配**：<768px 底部导航（按角色 ≤5 项，含未读角标与 `safe-area-inset-bottom`）、团购详情固定参团操作栏、弹窗 92% 宽度防溢出、按钮触摸目标 ≥44px；桌面布局不受影响
- **统一错误返回**：安全过滤链的 401/403 也走 `{code,msg,data}` JSON（未登录 401、无权限 403），500 兜底固定文案不泄漏内部异常；上传接口扩展名 + contentType 双重白名单校验

**明确不做**：商家入驻、配送骑手、佣金结算、真实支付、限购/优惠券/秒杀、评价审核/敏感词、团长回复评价、外部推送通道（短信/App 推送）、App/小程序。支付为 `MockPayServiceImpl` 模拟实现，通过 `PayService` 接口预留了接真实支付的扩展点。

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
│       │   ├── controller/        # Auth / Product / GroupBuy / Order / Review / Notification / Subscription / Admin / File / Health
│       │   ├── service/           # OrderService / GroupBuyService / ReviewService / ReviewAnalysisService /
│       │   │                      # SubscriptionService / NotificationService / AiClient /
│       │   │                      # DemandParseService(接口) + LlmParseService(LLM优先+规则降级) + RuleDemandParseService /
│       │   │                      # PayService(接口) + MockPayServiceImpl
│       │   ├── mapper/            # MyBatis-Plus Mapper（含原子扣减、超时关单等自定义 SQL）
│       │   ├── entity/            # User / Product / GroupBuy / GroupBuyItem / Order / Review / Notification /
│       │   │                      # UserSubscription / ReviewAnalysis
│       │   ├── security/          # SecurityConfig / JwtUtil / JwtAuthenticationFilter
│       │   ├── job/               # ScheduledJobs 定时任务
│       │   ├── dto/  common/  config/
│       └── resources/
│           ├── application.yml        # 端口 8080、JWT、上传路径、AI 解析配置（ai.*）
│           └── application-dev.yml    # 数据源（dev profile）
├── frontend/                      # Vue 3 前端
│   └── src/
│       ├── views/                 # 认证：Login / Register；布局：Dashboard / Home；AI 导购：AiShoppingAssistant
│       │                          # 用户：UserGroups / GroupBuyDetail / UserOrders / OrderDetail /
│       │                          #       ProductList / ProductDetail / MyNotifications
│       │                          # 团长：LeaderGroups / LeaderVerify
│       │                          # 管理：AdminProducts / AdminOrders / AdminDashboard
│       │                          # 通用：Profile（个人中心）/ NotFound（404）
│       ├── components/            # 公共组件（StatusBadge / GroupBuyCard / OrderCard / StatCard 等）
│       ├── styles/tokens.css      # 设计 token（品牌色，含 Element Plus 变量覆盖）
│       ├── api/                   # 按模块拆分的接口封装（含 subscription.js）
│       ├── stores/                # Pinia：user（token 与当前用户）、notification（未读计数）
│       ├── router/index.js        # 路由（未登录统一跳 /login）
│       └── utils/request.js       # Axios 实例
├── db/
│   ├── schema.sql                 # 建库建表（9 张）+ 三个基础账号 + 3 个商品
│   ├── seed.sql                   # 丰富的演示数据集（可重复执行，商品含分类与描述）
│   ├── migrate-2026-10-25.sql     # 增量迁移：product 表补 category/description（已有库升级用）
│   └── migrate-2026-10-25-subscription-match.sql  # 增量迁移：订阅匹配记录表 + 通知团购引用（已有库升级用）
└── openspec/                      # 需求与设计文档（capability 规格、任务清单）
```

---

## 快速开始

### 环境要求

Java 17、Maven 3.8+、Node.js 18+、MySQL 8+

### 1. 建库并导入数据

```bash
mysql -uroot -p --default-character-set=utf8mb4 < db/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 < db/seed.sql
```

> Windows 下务必带 `--default-character-set=utf8mb4`，否则中文会因 GBK 解析而报 `ERROR 1366`。
>
> **已有旧库时必须重跑 `schema.sql`**：它是 `DROP TABLE` + `CREATE TABLE` 的完整重建脚本。只跑 `seed.sql` 不会补建新增的表（例如 AI 导购的 `review_analysis`），后端启动后商品详情会报 `Table doesn't exist`，定时任务也会每 60 秒刷一次同样的错误。

- **`schema.sql`**：建库、建 9 张表（含需求订阅 `user_subscription` 与评价洞察 `review_analysis`），写入三个基础账号和 3 个商品
- **`seed.sql`**：**丰富的演示数据集**，可重复执行（每次清空业务表重建），时间全部是相对 `NOW()` 的，任何时候执行都能得到"看起来正在发生"的数据：

| 数据 | 规模 |
| --- | --- |
| 用户 | 16 个（1 管理员 / 3 团长 / 12 用户，密码统一 `123456`） |
| 商品 | 41 个生鲜商品，同一品类多品种多价格带（如苹果三档：花牛 ¥9.9 / 红富士 ¥12.9 / 阿克苏冰糖心 ¥15.9；含 AI 导购演示用的「红富士苹果」） |
| 团购 | 12 场 —— 进行中（进度 0/N、1/N、2/N）、即将截止、已成团待取货、已全部核销、已解散，另有一场历史苹果团用于口碑摘要 |
| 订单 | 30 笔 —— 待支付 / 待成团 / 已成团 / 已完成 / 已取消 / 已退款 |
| 评价 | 11 条（覆盖 4 个商品、2 个团长，含一条低分；苹果商品的 3 条评价用于演示口碑摘要） |
| 站内消息 | 27 条（成团 / 解散 / 核销 / 超时关单），部分未读 |

导入后管理员看板显示：今日订单 15、今日成团 3。

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

**AI 配置说明**：`application.yml` 默认已指向演示网关并写入演示 key（`AI_API_KEY` / `AI_BASE_URL` / `AI_MODEL` 环境变量可覆盖，生产请轮换 key 并从环境变量注入）。不配置 key 也能跑：订阅解析自动回落规则词典，即时推荐会明确提示"AI 服务暂时不可用"（不做假推荐）。

对应 `application.yml` 的 `ai.*` 四项（`base-url` / `api-key` / `model` / `timeout-seconds`），接口为 OpenAI 兼容协议，可指向 DeepSeek、通义等任意兼容供应商。留空 `AI_API_KEY` 时 `AiClient` 标记为不可用，需求解析与评价洞察均走规则实现。**配了 key 但端点不可达时**，5 秒超时后同样回落规则解析，接口不会报错。

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
| POST | `/api/auth/register` | 自助注册（用户/团长），返回 JWT | 公开 |
| POST | `/api/auth/login` | 登录，返回 JWT | 公开 |
| GET | `/api/auth/me` | 当前用户信息（含注册时间） | 登录 |
| POST | `/api/auth/change-password` | 修改密码（旧密码校验 + 6-32 位） | 登录 |
| PUT | `/api/auth/profile` | 维护个人资料（头像全角色；团长提货点名称/地址/照片） | 登录用户 |
| GET | `/api/products?keyword=&category=&sort=&page=&size=` | 在售商品检索（名称模糊 / 分类 / 排序白名单 / 分页） | 登录 |
| GET | `/api/products/categories` | 在售商品分类去重列表 | 登录 |
| GET | `/api/products/{id}` | 商品详情（含评分汇总与口碑标签） | 登录 |
| GET | `/api/products/admin-list?keyword=&status=&page=&size=` | 管理端全量商品列表（含下架） | 管理员 |
| POST/PUT/DELETE | `/api/products/**` | 商品增删改（含分类/描述；空分类落「其他」） | 管理员 |
| GET | `/api/group-buys` | 团购列表 | 登录 |
| GET | `/api/group-buys/mine` | 我发起的团购 | 团长 |
| GET | `/api/group-buys/mine/summary` | 团长经营概览（进行中团数 / 待取货 / 已核销 / 累计成交额） | 团长 |
| GET | `/api/group-buys/{id}` | 团购详情（含团长提货点与团长评分） | 登录 |
| POST | `/api/group-buys` | 发起团购 | 团长 |
| GET | `/api/group-buys/{id}/orders` | 该团购的订单列表（仅限本人发起的团购） | 团长 |
| POST | `/api/orders?groupBuyId=&quantity=` | 下单（原子扣库存） | 登录 |
| POST | `/api/orders/{id}/pay` | 模拟支付 | 登录 |
| POST | `/api/orders/{id}/cancel` | 取消（仅未成团） | 登录 |
| GET | `/api/orders/mine` | 我的订单（VO 含商品名/团购/团长/提货点） | 登录 |
| GET | `/api/orders/{id}` | 订单详情（本人或管理员） | 登录 |
| POST | `/api/orders/verify?pickupCode=` | 取货码核销 | 团长 |
| GET | `/api/orders/verify-history` | 本人最近核销记录（最多 10 条，不含买家身份） | 团长 |
| POST | `/api/orders/{id}/refund` | 退款兜底（订单管理页入口） | 管理员 |
| GET | `/api/admin/orders?status=&keyword=&page=&size=` | 管理端全量订单（keyword 匹配订单号或买家） | 管理员 |
| POST | `/api/reviews` | 提交评价（仅已完成订单，一单一评） | 登录 |
| GET | `/api/reviews/mine` | 我的评价 | 登录 |
| GET | `/api/reviews/product/{id}` | 商品评价列表与评分汇总 | 登录 |
| POST | `/api/ai/recommend` | AI 即时推荐（一句话需求 → 推荐回答 + 商品卡片） | 登录 |
| POST | `/api/subscriptions/parse` | 预览需求解析结果，不创建订阅 | 登录 |
| POST | `/api/subscriptions` | 创建需求订阅并立即匹配进行中的团购 | 登录 |
| GET | `/api/subscriptions` | 我的需求订阅 | 登录 |
| DELETE | `/api/subscriptions/{id}` | 取消本人订阅 | 登录 |
| GET | `/api/notifications` | 我的消息列表 | 登录 |
| GET | `/api/notifications/unread-count` | 未读消息数 | 登录 |
| POST | `/api/notifications/{id}/read` | 标记单条消息已读 | 登录 |
| POST | `/api/notifications/read-all` | 全部消息标记已读 | 登录 |
| GET | `/api/admin/dashboard` | 看板：今日订单/成团/成交额、注册用户、进行中团购、订单状态分布、近 7 日趋势 | 管理员 |
| POST | `/api/upload` | 上传图片（≤5MB，jpg/png/gif/webp）——商品图（管理页）、头像/提货点照片（个人中心） | 登录用户 |

---

## 测试

后端集成测试：真实 Spring 上下文 + MockMvc + 独立测试库 `freshcommunity_test`。

```bash
cd backend
DB_PASSWORD=<你的MySQL密码> mvn test
```

- 测试库由 `db/schema.sql` 自动初始化（库名替换为 `freshcommunity_test`），**不会动开发库 `freshcommunity`**
- 每个测试方法带事务，结束自动回滚，用例之间互不污染
- 数据库账号可用 `DB_USER` / `DB_PASSWORD` 环境变量覆盖，默认 `root` / `1234`
- 测试源码目前包含 **100 个用例**（15 个测试类），覆盖认证、商品检索分页与分类、订单详情与信息补全、修改密码与提货点维护、管理端订单列表、上传白名单与统一 401/403 口径、团购生命周期与库存、拼团价设置与校验、订阅匹配、LLM 解析降级、评价洞察、退款状态守卫、评价、站内消息与权限隔离、看板聚合口径、团长经营概览与核销记录；最近一次实测（2026-10-25）全绿

---

## 开发说明

- **团购的价格来自 `group_buy_item`，不在 `group_buy` 表上**：`group_buy_item.price_snapshot` 是拼团价，`product.price` 是零售价。团长开团时可显式指定拼团价（须 > 0 且不高于零售价），不填则以零售价快照兜底；两者有差距时前端展示「立省 X 元」。因此 `GET /api/group-buys` 与 `GET /api/group-buys/{id}` 返回的 VO 会额外带上 `productId / productName / imageUrl / price / originalPrice`；订单金额一律按 `price_snapshot × quantity` 计算。
- **数据库表名 `order` 是 MySQL 保留字**，实体上必须写成 `@TableName("`order`")`（带反引号），否则 MyBatis-Plus 生成的 `SELECT ... FROM order` 会报 `SQLSyntaxErrorException`。
- **JWT 无法主动失效**：token 签发后到期前一直有效。毕设场景可接受，生产需引入黑名单或改用短 token + refresh token。
- **密码存储**：BCrypt 哈希，`$2a$10$` 前缀，长度 60。手写 `schema.sql` 时注意别写坏哈希长度，否则登录会恒定失败。
- 商品图片上传到 `${user.dir}/uploads`，通过 `/uploads/**` 静态映射访问，该目录已在 `.gitignore` 中忽略。

---

## 需求文档

`openspec/` 目录下有 **12 个 capability 基线**（62 条 Requirement / 139 个 Scenario）、**8 个已归档变更**。

```
openspec/
├── specs/                          # 基线：系统当前真实行为
│   ├── user-roles/  product-catalog/  group-buying/  inventory/
│   ├── order-lifecycle/  pickup-verification/  admin-dashboard/
│   ├── reviews/  notifications/
│   └── ai-subscription/  review-insights/   # AI 导购（2026-10-17 归档时并入）
└── changes/
    └── archive/                    # 已归档变更（提案 / 增量规格 / 设计 / 任务清单）
        ├── 2026-10-11-mvp-group-buying/
        ├── 2026-10-11-reviews-and-notifications/
        ├── 2026-10-17-ai-shopping-assistant/
        ├── 2026-10-23-admin-dashboard-and-leader-ui/
        ├── 2026-10-25-catalog-retrieval-user-center-admin/
        ├── 2026-10-25-subscription-match-reliability/
        ├── 2026-10-25-mobile-adaptation/
        └── 2026-10-25-real-images-avatars/
```

每个变更目录含 `proposal.md`（为什么做、边界）、`specs/`（delta 增量规格）、`design.md`（技术方案与取舍）、`tasks.md`（任务清单）；归档时 delta 合并进基线。完整工作流说明见 [`docs/openspec-guide.md`](docs/openspec-guide.md)。
