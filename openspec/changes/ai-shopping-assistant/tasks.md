# Tasks

## 1. 数据库与 LLM 基础

- [ ] 1.1 `db/schema.sql` 追加 `user_subscription`、`review_analysis` 两张表（保持脚本可重复执行），验证：`mysql -uroot -p1234 --default-character-set=utf8mb4 < db/schema.sql` 连续执行两次无报错，`SHOW TABLES` 显示 9 张表
- [ ] 1.2 LLM 客户端封装（OpenAI 兼容接口；配置项 `ai.base-url / ai.api-key / ai.model / ai.timeout`；无 key 时标记"不可用"而非抛错），验证：无 key 环境下应用正常启动且客户端状态为不可用；`mvn compile` 通过
- [ ] 1.3 规则解析器（正则覆盖 `X 元以内/以下的 Y` 类表述），验证：新增集成用例断言 `"10 元以内的苹果"` → `{keyword: 苹果, maxPrice: 10}`，非标表述返回解析失败

## 2. 需求订阅（后端）

- [ ] 2.1 `DemandParseService`：LLM 优先、超时/异常/结果不合法时回落规则解析，验证：无 key 环境下走规则解析成功；两条链路均失败时返回明确错误
- [ ] 2.2 订阅创建 / 列表 / 取消接口（含归属校验与解析回显），验证：新增 `AiSubscriptionApiTest` 断言——创建成功并回显解析结果、列表只含本人订阅、取消生效、跨用户操作被拒
- [ ] 2.3 开团匹配：`GroupBuyService.createGroupBuy` 创建后匹配有效订阅（商品名 + 拼团价），命中即 `notify()`（`SUBSCRIPTION_MATCH` 类型），验证：集成用例断言——订阅后开命中团收到通知、开不命中团无通知、同一团购不重复通知
- [ ] 2.4 订阅时立即匹配进行中团购，验证：集成用例断言——已存在命中团时订阅成功即收到通知

## 3. 评价洞察（后端）

- [ ] 3.1 评价分析服务：LLM 优先、规则词典降级，生成情感倾向与关键词标签并写入 `review_analysis`（review_id 唯一），验证：集成用例断言样例文本的情感与标签生成正确、重复分析同一评价不产生重复记录
- [ ] 3.2 定时批量分析任务：每分钟扫描"有文字且无分析记录"的评价，验证：造一条评价后触发任务，`review_analysis` 出现对应记录；无文字评价不产生记录
- [ ] 3.3 口碑标签聚合查询并附加到商品 / 团购详情接口（`insightTags`），验证：集成用例断言——有分析数据时返回高频标签、无数据时不返回或为空
- [ ] 3.4 提醒附带口碑摘要：商品有洞察时 `SUBSCRIPTION_MATCH` 通知内容附加摘要行，验证：集成用例断言——有分析数据时通知含摘要、无数据时不含摘要且通知照常发送

## 4. 前端

- [ ] 4.1 「AI 导购」页：需求输入 + 解析结果回显 + 订阅列表与取消（路由 `/user/ai` + 用户菜单入口），验证：`vite build` 通过；页面完成一次订阅与取消的完整走查
- [ ] 4.2 商品详情 / 团购详情口碑标签区（无标签整块隐藏），验证：构建通过；有洞察数据的商品展示标签、无数据的商品布局不变
- [ ] 4.3 消息中心支持 `SUBSCRIPTION_MATCH` 类型（图标 + 文案，`constants/status.js` 扩展），验证：构建通过；该类型通知在列表正确渲染
- [ ] 4.4 后端全量回归：`cd backend && DB_PASSWORD=1234 mvn test`，验证：全部用例通过（既有 30 个 + 本变更新增用例），无回归失败

## 5. 端到端整合

- [ ] 5.1 端到端演示脚本实测：启动前后端 → `user1` 订阅"10 元以内的苹果" → `leader1` 开 8.8 元苹果团 → `user1` 收到带口碑摘要的站内信 → 前往参团，验证：LLM 有 key 与无 key（规则降级）两种模式各完整跑通一次
- [ ] 5.2 项目文档同步：`README.md`（功能与接口清单）、`docs/implementation-inventory.md`（新功能状态与验证证据）、`docs/requirements-analysis.md`（AI 导购助理需求并入），验证：三份文档与实现一致，数字经实测统计

## Workflow follow-up

- 全部任务完成后，走 `openspec archive` 归档本变更并同步主规格（新增 ai-subscription、review-insights 两个 capability）。
- 归档前抽查 tasks 勾选项与实现一致性（本项目有虚报先例，须逐条核对验证证据）。
- 归档后更新 `docs/ui-design/`、答辩演示脚本与论文素材索引中涉及本变更的内容。
