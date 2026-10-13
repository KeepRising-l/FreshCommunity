# Design

## Context

现状约束（动机见 proposal.md - Why）：

- 已交付 9 capability（交易闭环 + 评价 + 站内信），既有 7 张表；MyBatis-Plus + 逻辑删除；统一返回 `{code,msg,data}`；JWT 无状态鉴权。
- 站内信体系就绪：`NotificationService.notify(userId, type, title, content, refOrderId)`，5 类事件同事务写入；`notification.type` 为 VARCHAR(32)，扩展类型无需改表。
- `GroupBuyService.createGroupBuy` 为开团入口；拼团价存于 `group_buy_item.price_snapshot`。
- **`product` 表无 category 字段**（只有 name），匹配只能基于商品名。
- 项目约束：不引入 Redis / 消息队列；演示环境为本地单机；无 LLM 依赖的既有经验，需自带降级。

## Goals / Non-Goals

**Goals:**

- 订阅链路完整可演示：一句话需求 → 结构化条件 → 开团匹配 → 站内信触达（复用既有通知通道）
- LLM 可插拔、可降级：无 API key 或调用失败时，常见表述仍可订阅、洞察仍可生成（规则兜底）
- 评价洞察异步生成，零侵入评价主流程（提交成功不依赖分析成功）
- 所有新能力可测试：新增接口全部补集成回归用例

**Non-Goals:**

- 不做多轮对话、会话上下文管理
- 不自研模型、不做微调；不做向量检索 / Embedding（关键词 + 价格阈值足够）
- 不做订阅复杂条件（品类树、时间窗、多商品组合）
- 不做消息跳转直达团购（首版通知不含 ref 团购关联）
- 不做基于洞察的推荐排序（收敛为"标签展示 + 提醒摘要"）

## Decisions

**1. LLM 调用封装为可插拔解析器 + 规则降级链**

- 定义 `DemandParseService` 接口：`parse(String text) -> {keyword, maxPrice}`。
- 两个实现：
  - `LlmParseService`：调用 OpenAI 兼容接口（base-url / api-key / model 均可配置，默认建议国产供应商如 DeepSeek / 通义）；请求时携带当前在售商品名列表，让模型把口语词归一映射到实际商品词；超时 5 秒。
  - `RuleParseService`：正则解析常见句式（`(\d+(?:\.\d+)?)\s*元?(?:以内|以下|以里)` + 商品词提取），覆盖"10 元以内的苹果"等表述。
- 择优策略：配置了 key 则优先 LLM，超时/异常/结果不合法（keyword 为空、价格为负）时回落规则；两者均失败返回"无法解析"提示。
- 备选：引入 Spring AI / LangChain4j —— 依赖重、API 抽象层学习成本高，本项目仅一处解析调用，否决。

**2. 订阅表 `user_subscription`（第 8 张表）**

```sql
user_subscription(
  id, user_id BIGINT NOT NULL,
  keyword VARCHAR(64) NOT NULL,        -- 归一后的商品关键词
  max_price DECIMAL(10,2) NULL,        -- 价格上限，可为空（用户未提价格）
  source_text VARCHAR(255),            -- 原始输入，用于回显与追溯
  created_at, deleted,
  INDEX idx_user (user_id))
```

- 取消订阅 = 逻辑删除（`deleted=1`），与项目既有惯例一致，不加 status 字段。
- `source_text` 保留用户原话，回显与调试都有用。

**3. 匹配触发点与防重复策略**

- 触发两处（均在 `GroupBuyService.createGroupBuy` 内与订阅创建时）：
  - **开团触发**：新团购创建后，取该团商品与 `price_snapshot` 匹配全部有效订阅（`product.name LIKE %keyword%` 且 `price_snapshot <= max_price`），命中即逐用户 `notify()`。
  - **订阅触发**：创建订阅后立即对进行中团购做一次同样匹配（对应"订阅即有货"场景）。
- 防重复（"同一订阅对同一团购只提醒一次"）：**不引入匹配记录表**。两个触发路径匹配的团购集合互斥（订阅触发只匹配"已存在"的团，开团触发只匹配"新建"的团），同一团购对同一订阅不存在二次触发路径。若未来增加周期性扫描，再引入记录表。
- 价格匹配对象为拼团价 `price_snapshot`（用户参团实付价）。

**4. 通知扩展：新增 `SUBSCRIPTION_MATCH` 类型**

- 复用 `NotificationService.notify()`；`refOrderId` 传 `null`（订阅提醒不关联订单，首版不做跳转）。
- 提醒文案结构：`「你关注的『苹果』出现了：¥8.80/份，还差 2 人成团」` + 可选口碑摘要行。
- 前端 `constants/status.js` 扩展通知类型映射（图标 + 文案）。

**5. 评价洞察：新表 `review_analysis` + 定时批量分析**

```sql
review_analysis(
  id, review_id BIGINT UNIQUE,         -- 1:1 关联评价
  sentiment VARCHAR(16),               -- POSITIVE / NEGATIVE / NEUTRAL
  tags VARCHAR(255),                   -- 逗号分隔关键词，如"新鲜,实惠"
  analyzed_at, deleted)
```

- **触发方式选定时批量**（`ScheduledJobs` 每分钟扫描未分析的有文字评价）而非 `@Async`：
  - 与项目现有技术形态完全一致（现成 @Scheduled 模式），无新增线程池配置；
  - 天然重试（失败下轮再试），无需异常处理管道；
  - 延迟 ≤60 秒，演示可接受。备选 @Async 否决（引入线程池与失败处理复杂度，收益仅是秒级时延）。
- 分析实现：`ReviewAnalysisService.analyze(text)` 优先 LLM（同 `LlmParseService` 的客户端封装），失败回落规则词典（"新鲜/甜/快/实惠"→ 正面标签；"烂/贵/慢/差"→ 负面），保证无 key 环境演示仍有数据。
- 不改 `review` 表（沿用"新能力用新表、既有表零改动"的惯例）。
- **展示聚合在 Service 层内存完成**：查该商品全部 analysis 记录 → 词频 top 5 → 接口返回标签列表；数据量小，不做 SQL 复杂聚合。商品详情与团购详情接口附加 `insightTags` 字段。
- 提醒中的口碑摘要：取命中商品标签 top 3 拼接（如"已有 3 人评价都说新鲜"——条数取该商品被分析的评价数）。

**6. 前端落点**

- 新增「AI 导购」页（路由 `/user/ai`）：自然语言输入框 → 解析结果卡片（关键词 + 价格上限，可确认/重填）→ 我的订阅列表（含取消）。
- 导航：用户角色菜单加「AI 导购」入口。
- 商品详情 / 团购详情：口碑标签区（无标签时整块隐藏）。
- 消息中心：`SUBSCRIPTION_MATCH` 类型的图标与文案。
- 界面沿用一/二期设计系统（tokens + 公共组件）。

**7. 演示脚本（写入答辩材料备用）**

1. `user1` 打开「AI 导购」，输入："想要 10 元以内的苹果，有货叫我"；
2. 页面回显解析卡片："商品：苹果 · 价格 ≤ ¥10"；（若现场已有命中团，立即弹出提醒演示）；
3. `leader1` 开一个 8.8 元的苹果团；
4. `user1` 的消息中心收到："你关注的『苹果』出现了……已有 N 人评价都说甜"；
5. `user1` 前往参团 → 接回主交易闭环。

## Risks / Trade-offs

- [LLM 解析不稳定 / 供应商波动] → 5 秒超时 + 规则降级 + 解析结果回显（用户可见可修正）；解析粒度粗（关键词 + 价格）本身容错高
- [关键词子串匹配偏粗（无品类字段）] → 解析时注入商品名列表做归一；演示数据商品名清晰；后续可加 category 字段演进
- [定时分析延迟 ≤60 秒] → 演示节奏可接受（讲解期间自然完成）；数据量小无性能压力
- [标签内存聚合随数据增长变慢] → 当前规模无影响；预留后续 SQL 聚合 / 冗余列演进
- [通知不含团购跳转，体验打折] → 首版明确不做；演示讲解走"消息中心 → 广场"路径
- [LLM 调用成本] → 订阅解析仅用户操作时触发；评价分析每分钟批量且只处理未分析记录，量级极小

## Migration Plan

1. `db/schema.sql` 追加 `user_subscription`、`review_analysis` 两张表（脚本保持可重复执行）
2. 新表为纯增量，既有数据与接口不受影响
3. 回滚：删除两张表 + 移除新模块代码，无数据迁移负担

## Open Questions

- LLM 供应商最终选型（部署时按可用性与成本定，接口为 OpenAI 兼容、配置化，不影响本设计与任务拆分）
- 口碑标签的视觉样式（实现时对照设计系统定稿）
