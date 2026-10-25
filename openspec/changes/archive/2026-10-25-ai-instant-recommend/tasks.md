# Tasks

## 1. 后端

- [x] 1.1 `application.yml`：ai.base-url 默认 `https://rayagent.cloud:2026/v1`、model 默认 `deepseek-v4.1-flash`、api-key 默认写入提供的 key（环境变量可覆盖）、timeout-seconds 默认 60；新增 `ai.thinking-enabled: false`；`application-test.yml` 覆盖 `ai.api-key: ""`，验证：`mvn compile` 通过、测试环境不外呼
- [x] 1.2 `AiProperties` 加 `thinkingEnabled` 字段；`AiClient` 请求体在 thinkingEnabled=false 时带 `chat_template_kwargs: {"thinking": false}`，验证：现有 LlmParseServiceTest 不回归
- [x] 1.3 新建 `dto/AiRecommendVO{reply, items: List<ItemVO{productId, groupBuyId, name, price, originalPrice, imageUrl, category, insightTags, groupTitle, memberCount, targetCount}>}`；新建 `AiRecommendService.recommend(text)`：组装在售商品+进行中团购+口碑标签候选 → LLM 生成回答（三段式提示词，150 字内）→ 名称回扫提取被点名商品（长名优先、≤6 个、挂团购优先）；LLM 不可用抛"AI 服务暂时不可用"，验证：`mvn compile`
- [x] 1.4 新建 `AiRecommendController`：`POST /api/ai/recommend`（登录，text 必填 ≤255），验证：`mvn test` 全量绿（新增单测 mock AiClient：候选含在团价与口碑、点名提取长名优先、不可用报错；集成断言无 key 环境 400 提示）

## 2. 前端

- [x] 2.1 新建 `api/ai.js`（recommend）；`AiShoppingAssistant.vue` 重构两段式：「01 AI 帮我挑」输入 → 问 AI（loading 文案"正在对比在售商品…"）→ 回答文本（pre-line）+ 推荐商品卡片（图/名/拼团价/口碑，点击跳团购或商品详情）+「订阅这个需求」按钮衔接原解析流；原「写下你的需求」区降为「02 订阅提醒」，验证：`npm run build` 通过
- [x] 2.2 浏览器实测：输入"帮我找一下有没有性价比比较高的苹果"→ 展示 AI 回答与苹果卡片（花牛/红富士），卡片可点击；LLM 网关断开场景展示可重试报错，验证：截图记录

## 3. 验证与文档

- [x] 3.1 端到端实测：真实 LLM 调用 `POST /api/ai/recommend` 断言 reply 非空且包含"推荐"、items 命中苹果系商品；追加 `docs/verification-report.md` §十二
- [x] 3.2 同步 README（AI 导购两段式说明、key 配置与轮换提示、接口表）与 `docs/implementation-inventory.md`（接口计数 +1、FR-11 更新）
- [x] 3.3 归档变更并 `openspec validate --specs` 通过
