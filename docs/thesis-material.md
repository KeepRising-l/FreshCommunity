# 毕业设计文档写作素材（项目侧索引）

> **用途**：把本项目已有的产出，整理成写**开题报告 / 任务书 / 论文**时可直接引用的素材地图。
> **边界**：本文件不替代论文本身，只做「素材定位 + 关键数据 + 图表清单 + 风险提示」。
> **生成时间**：2026-10-11（首版）；**数据刷新**：2026-10-14（源码统计；未执行的验证不计为通过）

---

## 一、项目一句话

**FreshCommunity —— 社区生鲜团购平台**。围绕「团长开团 → 邻居参团 → 人数达标成团 → 提货点自提核销」的核心闭环，实现用户 / 团长 / 管理员三角色的完整业务流程；团购到点未达标自动解散并全额退款。

**技术栈**：Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + Spring Security + JWT（Java 17）｜ Vue 3.4 + Element Plus + Pinia + Vite ｜ MySQL 8

---

## 二、可量化的项目规模

> 写「工作量」「系统规模」章节时用。数量按源码与文件统计；运行结论以实测报告为准。

| 维度 | 数量 |
| --- | --- |
| 后端 Java 文件 | **60** 个 / 约 **2075** 行（`backend/src/main/java`） |
| 前端 Vue 文件 | **22** 个 / 约 **4865** 行（`frontend/src`） |
| 数据库表 | **9** 张已建（含 `user_subscription`、`review_analysis`） |
| 后端接口 | **33** 个 |
| 功能能力（capability） | **9** 个基线能力，35 条需求、57 个场景；AI 导购为活动变更 |
| 自动化测试 | **47** 个测试方法（7 个测试类，最近一次 `mvn test` 全绿） |
| 规格变更 | **2** 个已归档，`ai-shopping-assistant` 进行中 |
| 演示数据规模 | 16 用户 / 16 商品 / 11 场团购 / 27 笔订单 / 8 条评价 / 27 条站内消息 |

---

## 三、素材地图：按论文常见章节对应

| 论文章节 | 可直接引用的素材 | 位置 |
| --- | --- | --- |
| **选题背景与意义** | 社区团购业务闭环描述、三角色诉求分析 | `docs/ui-design/design-spec.md` §1 |
| **需求分析** | 9 个 capability 的完整需求与验收场景（含 WHEN/THEN） | `openspec/specs/*/spec.md` |
| **需求优先级与范围** | 明确「做什么 / 不做什么」的边界声明 | `openspec/changes/archive/2026-10-11-mvp-group-buying/proposal.md` |
| **系统总体设计** | 分层架构、模块划分、技术选型理由 | `openspec/changes/archive/.../design.md` |
| **数据库设计** | 9 张已建表的 DDL、索引设计 | `db/schema.sql`、`docs/design-document.md` |
| **关键机制设计** | 库存防超卖（原子扣减）、状态机、定时任务、并发成团判定 | 同上 `design.md` + `openspec/specs/inventory/spec.md`、`order-lifecycle/spec.md` |
| **界面设计** | 设计原则、信息架构、视觉语言、双端原型 | `docs/ui-design/design-spec.md`、`prototype.html`、`prototype-mobile.html` |
| **系统实现** | 各模块接口清单与权限 | `README.md` 接口一览 |
| **系统测试** | 实测验证方法与结论、47 个测试源码方法 | `docs/verification-report.md`、`backend/src/test/` |
| **过程管理** | 需求→实现→归档的可追溯轨迹，含当前活动变更 | `openspec/changes/`（proposal / design / tasks / specs） |
| **开发规范与工具** | 规格驱动开发方法论 | `docs/openspec-guide.md` |

**这个项目写论文最大的优势**：需求不是事后补的，而是**先写规格、再实现、最后归档**，每个功能都有可追溯的提案与验收场景。这一点在「需求分析」「过程管理」章节是加分项，建议单独成节写。

---

## 四、建议的图表清单

| 图 | 内容 | 素材来源 |
| --- | --- | --- |
| 系统架构图 | 前端 / 后端 / 数据库三层 + 定时任务 | `design.md`、项目结构 |
| 用例图 | 三角色的功能边界 | `openspec/specs/user-roles/spec.md` |
| ER 图 | 9 张已建表 | `db/schema.sql`、`docs/design-document.md` |
| **团购状态机图** | 进行中 → 已成团 / 已解散 | `openspec/specs/group-buying/spec.md` |
| **订单状态机图** | 待支付 → 待成团 → 已成团 → 已完成，及终态 | `openspec/specs/order-lifecycle/spec.md` |
| 时序图 | 下单→支付→达标成团的并发判定 | `design.md` 决策 4 |
| 时序图 | 到点未成团→自动解散→退款 | `specs/group-buying/spec.md` |
| 界面原型图 | 三角色关键页面 | `docs/ui-design/preview/*.png` |
| 测试用例分布图 | 7 个测试类 / 47 个方法 | `backend/src/test/` |

---

## 五、开题报告 / 任务书可直接落地的点

- **研究内容**：社区团购交易闭环、库存并发控制、订单状态机、定时任务调度、权限隔离
- **技术路线**：规格驱动开发（OpenSpec）→ Spring Boot 分层实现 → 集成测试验证
- **关键技术难点**（建议重点写）：
  1. **并发下单防超卖** —— 用 `UPDATE ... WHERE stock >= n` 原子扣减，而非「先查后改」
  2. **并发重复成团** —— 条件更新（`trySuccess`）保证只成功一次
  3. **人数与订单一致性** —— 取消/退款时必须回退 `member_count`，否则产生「虚假成团」
- **进度安排**：可直接用两个已归档变更的时间线（提案 → 实施 → 归档）作为依据

---

## 六、⚠️ 不能写进文档的内容（避免答辩被追问）

**明确不做的功能**（写进"未来工作"而非"已实现"）：

- 真实支付 —— 目前是 `MockPayServiceImpl` 模拟实现，通过 `PayService` 接口预留了扩展点
- 配送 / 骑手、商家入驻、佣金结算
- 限购 / 优惠券 / 秒杀
- 短信 / App 推送（站内消息仅为站内可见）
- 小程序 / App

**已知技术限制**（如被问及，如实说明比硬撑好）：

- JWT 无法主动失效（无黑名单 / refresh token）
- 所有列表**无分页**
- 前端主包 **1.27MB**（Element Plus 全量引入，未做按需）
- 评价不支持修改/删除、无审核与敏感词过滤
- 无前端自动化测试；后端源码含 47 个集成测试方法，最近一次 `mvn test` 全绿

---

## 七、系统测试章节的证据

- **方法学**：不读代码下结论，而是对**真实运行的后端发请求**做可执行断言（见 `docs/verification-report.md`）
- **自动化用例分布**：

| 测试类 | 用例数 | 覆盖 |
| --- | --- | --- |
| `AuthApiTest` | 8 | 登录 / 注册 / 401 / 404 / 405 / 越权注册管理员 |
| `GroupBuyFlowApiTest` | 10 | 列表/详情字段、库存、取消回退人数、退款守卫、超时关单、团长订单归属与权限 |
| `ReviewApiTest` | 7 | 评价资格、重复 / 越界、评分汇总、无评价不误导 |
| `NotificationApiTest` | 5 | 六类事件通知、跨用户隔离、未读数 |
| `SubscriptionApiTest` | 9 | 需求解析、预览不落库、归属隔离、新团与已有团匹配 |
| `LlmParseServiceTest` | 3 | 无 key 回落规则解析、非标表述、无价格需求 |
| `ReviewInsightsTest` | 5 | 情感与标签、重复分析幂等、批量跳过无文字评价、详情标签、提醒摘要 |

- **运行方式**：`cd backend && DB_PASSWORD=1234 mvn test`（独立测试库 `freshcommunity_test`，不影响开发库）
- **历史缺陷修复**：MVP 首轮实测发现 3 个真实缺陷（取消订单不回退人数、退款无状态守卫、登录失败未返回 401），均已修复并**转化为回归用例** —— 这是"测试驱动质量"的实证，值得在论文中写成一小节
