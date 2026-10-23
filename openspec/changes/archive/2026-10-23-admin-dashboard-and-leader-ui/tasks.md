# Tasks

## 实施顺序

三块后端接口彼此独立，按 1 → 2 → 3 推进（看板体量最大，先做透）；每组自带测试与接口文档，不留到最后一组补。前端两组的顺序必须跟在其依赖的接口组之后。时间紧张时的降级顺序：先保 1 + 4（管理看板完整交付），再 3 + 5.2（核销台），最后 2 + 5.1（团长概览）。

## 1. 管理看板数据（后端）

- [x] 1.1 新建 `dto/AdminDashboardVO`（`todayOrders` / `todaySuccessGroups` 字段名沿用现有响应，新增 `todayGmv`、`totalUsers`、`ongoingGroupBuys`、`orderStatusDistribution`、`trend7d`），验证：`mvn compile` 通过；Jackson 序列化后的 JSON 仍含原有两个键
- [x] 1.2 `OrderMapper` 增加三条只读聚合：今日成交额（`COALESCE(SUM(amount),0)`，排除 `PENDING_PAYMENT`/`CANCELLED`/`REFUNDED`）、订单状态分布（`GROUP BY status`）、近 7 日按 `DATE(created_at)` 分组的订单数与成交额，验证：三条 SQL 全部显式带 `deleted = 0`；在测试库手工执行返回预期行数
- [x] 1.3 `UserMapper` 增加注册用户总数、`GroupBuyMapper` 增加进行中团购场数查询，验证：应用启动后调用接口，两个数字与数据库直查 `COUNT(*)` 一致
- [x] 1.4 看板组装逻辑（新建 `AdminDashboardService` 或在 `AdminController` 内组装）：生成含当日在内连续 7 个日期骨架，SQL 稀疏结果按日填充，缺失日 `orders=0, gmv=0`，验证：测试库只造第 1 天与第 5 天的订单，返回数组长度为 7 且其余 5 个点为 0
- [x] 1.5 把既有 `OrderMapper.countTodayOrders` 与 `GroupBuyMapper.countTodaySuccess` 补上 `deleted = 0`，验证：`mvn test` 全量回归无失败；新增用例断言 `deleted=1` 的今日订单既不计入今日订单数也不计入成交额
- [x] 1.6 新建 `DashboardApiTest`，覆盖规格场景：成交额只计已支付口径 / 待支付与取消与退款不计 / 今日无成交返回 0 / 状态分布与库内分组一致 / 趋势 7 点连续补零 / 8 日前订单不进窗口 / 用户与团长请求看板返回 403，验证：用例全部通过且不依赖 `db/seed.sql` 的相对时间（日期在用例内显式构造）
- [x] 1.7 更新 `README.md` 接口一览中 `/api/admin/dashboard` 的说明（标注返回 KPI + 状态分布 + 7 日趋势），验证：文档描述与实测响应字段逐一对得上

## 2. 团长经营概览（后端）

- [x] 2.1 新建 `dto/LeaderSummaryVO{ongoingGroups, pendingPickupOrders, completedOrders, totalGmv}` 与对应 Mapper 查询（join `group_buy` 限定 `leader_id`，成交额状态口径与 1.2 完全一致），验证：两条团长各自的种子数据下，两个团长的数字互不影响
- [x] 2.2 新增 `GET /api/group-buys/mine/summary`（`@PreAuthorize("hasRole('LEADER')")`），验证：`leader1` 拿到本人数字；`user1` 请求返回 403；响应不含其他团长的任何数据
- [x] 2.3 新建 `LeaderConsoleApiTest` 补用例：概览只含本人数据 / 订单全部处于未成交状态时 `totalGmv` 为 0 而非 null / 待取货与已核销计数与库内一致，验证：新增用例通过且原有 12 个用例无回归
- [x] 2.4 更新 `README.md` 接口一览新增该接口（权限标注团长），验证：按文档命令用 `leader1` 的 token 能调通

## 3. 最近核销记录（后端）

- [x] 3.1 新建 `dto/VerifyRecordVO{orderNo, groupBuyTitle, quantity, amount, verifiedAt}` 与 `OrderMapper` 查询（本人名下、`status='COMPLETED'`、`deleted=0`、按 `updated_at DESC LIMIT 10`），验证：SQL 直查结果与预期订单集合一致，且返回列中不含 `user_id`
- [x] 3.2 新增 `GET /api/orders/verify-history`（团长角色、条数固定 10 不接客户端参数），验证：核销一单后立刻调用，该单在首位；传 `?limit=100` 之类的参数不影响返回条数上限
- [x] 3.3 在 `LeaderConsoleApiTest` 补用例：本人核销记录可见 / 其他团长记录不可见 / 超 10 笔只回最近 10 笔 / 无核销时返回空数组 / 响应不含买家身份字段，验证：用例通过
- [x] 3.4 更新 `README.md` 接口一览新增该接口，验证：文档路径、方法、权限与实现一致

## 4. 管理端看板界面

- [x] 4.1 `api/admin.js` 补充看板返回结构的消费方式（新增字段直接使用，无需新接口），验证：页面能取到 `todayGmv` / `trend7d` / `orderStatusDistribution`
- [x] 4.2 `AdminDashboard.vue` 顶部改为 `StatCard` KPI 行（今日订单、今日成团、今日成交额、注册用户、进行中团购），金额用 `fc-num` 与两位小数，验证：以 `admin` 登录实测渲染 5 张卡且数字与接口返回一致
- [x] 4.3 手绘 SVG 近 7 日趋势（`viewBox` + `polyline` + 面积填充 + 7 个数据点，日期与数值标注在图外的文字节点），不引入图表库，验证：`package.json` 依赖无变化；`vite build` 通过；窄容器下图形随宽度缩放而文字不挤叠
- [x] 4.4 订单状态分布水平条（`div` 宽度百分比 + `constants/status.js` 的 tone 配色 + 计数标注），无订单时用 `EmptyState` 空态，验证：浏览器实测各条宽度比例与接口计数一致；清空订单表后区域显示空态而非空白
- [x] 4.5 看板页实测记录：把 4.2～4.4 的断言追加进 `docs/verification-report.md` 本轮小节，验证：报告中每条断言附实际响应片段或截图路径

## 5. 团长端界面

- [x] 5.1 `api/groupBuy.js` 增加 `getMyGroupBuySummary`，`LeaderGroups.vue` 顶部加 `StatCard` 概览行（进行中 / 待取货 / 已核销 / 累计成交额），验证：`leader1` 登录后四个数字与接口返回逐项一致
- [x] 5.2 `LeaderGroups.vue` 团购列表补两列：「还差几人」（`targetCount - memberCount`，已成团显示"已达标"）与「核销进度」（该团已核销/待取货数，来自现有订单接口或概览数据），验证：种子数据下两种状态的团展示文案正确
- [x] 5.3 `LeaderVerify.vue` 重构为核销台专注模式：单列居中窄容器、大号等宽取货码输入、≥46px 主按钮、页面内结果条（成功/失败常驻显示，取代只有 toast）、安全边界说明（仅限本团 / 防重复），验证：`vite build` 通过；浏览器实测成功与无效码两种反馈都可见，失败时输入内容保留
- [x] 5.4 `api/order.js` 增加 `listVerifyHistory`，`LeaderVerify.vue` 接入最近核销记录列表 + 核销成功后刷新记录并把焦点交还输入框，验证：连续核销两单后列表首两条顺序正确，且无需手动刷新页面
- [x] 5.5 团长端两页实测记录追加进 `docs/verification-report.md`，验证：断言与页面实际显示一致

## 6. 整合实测

- [x] 6.1 后端全量回归：`cd backend && DB_PASSWORD=<密码> mvn test`，验证：原有 52 个用例与本变更新增用例全部通过，无回归失败
- [x] 6.2 端到端走查：`user1` 参团下单支付 → 达标成团拿到取货码 → `leader1` 在核销台输码核销 → 核销记录出现该单 → `leader1` 概览的待取货/已核销/成交额随之变化 → `admin` 看板 KPI、状态分布与趋势同步，验证：整条链路在真实运行的前后端上跑通一次并留存证据
- [x] 6.3 文档同步：`docs/implementation-inventory.md`（FR-7-3 / FR-10-3 / FR-10-4 转 ✅、接口数 33 → 35、页面视觉状态列更新、测试用例数按实测统计）、`docs/ui-design/design-spec.md` §6.3/§6.4 若实现与设计有偏差则就地标注实际取舍，验证：文档中的数字全部来自实测统计，不允许沿用改动前的旧值

## Workflow follow-up

- 归档前抽查 tasks 勾选项与实现是否一致（本项目有虚报先例），并确认规格与实现对齐
- 归档后运行 `openspec validate --specs` 校验基线
- 归档时把三个 delta 合并进 `admin-dashboard` / `group-buying` / `pickup-verification` 基线
