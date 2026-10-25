# Proposal

## Why

现有 AI 导购只有「订阅提醒」一种交互：用户写需求 → 解析成关键词 → 持续匹配团购。用户实测反馈这与直觉不符——输入「帮我找一下有没有性价比比较高的苹果」，系统把它当订阅条件存起来等提醒，而用户想要的是**现在就得到回答**：把在售的苹果找出来对比，直接推荐一个并说明理由。

同时平台此前仅在配置 LLM key 时才启用智能解析（无 key 走规则词典），本次接入用户提供的真实 LLM 服务（OpenAI 兼容网关 + DeepSeek 模型），让「即时推荐」和「订阅解析」都由大模型驱动。

## What Changes

- **新增 AI 即时推荐**：`POST /api/ai/recommend`（登录用户）。流程：汇总在售商品（含分类/零售价/库存/描述）、进行中团购（拼团价/成团进度/剩余时间/提货点）、商品口碑标签 → 组装候选清单交给 LLM → 生成结构化推荐回答（首选 + 备选 + 小贴士，中文 ≤150 字）→ 后端从回答中识别被点名的商品，连同对应团购卡片一并返回给前端渲染
- **LLM 配置落地**：`ai.*` 默认值指向用户提供的 OpenAI 兼容网关（`https://rayagent.cloud:2026/v1`、模型 `deepseek-v4.1-flash`），key 以默认值写入并由环境变量 `AI_API_KEY` 覆盖；请求侧按 DeepSeek 标准方式携带关闭思考模式参数（当前网关未透传该开关，思考内容在独立字段、不进入最终回答，正文与延迟不受影响）；超时放宽到 60 秒
- **不做 mock**：LLM 不可用或调用失败时明确报错「AI 服务暂时不可用」（不做假推荐）；无 key 环境订阅解析仍回落规则词典（既有行为不变）
- **前端**：AI 导购页重构为两段式——「01 AI 帮我挑」即时问答（输入需求 → AI 回答 + 推荐商品卡片，可点击跳转团购详情；可一键把该需求转为订阅）；「02 订阅提醒」保留原有解析/订阅流程
- **明确不做**：多轮对话（单问单答）、流式输出、语音输入、跨会话记忆

## Capabilities

### Modified Capabilities

- `ai-subscription`: 新增「AI 即时推荐」Requirement——登录用户一句话需求 SHALL 得到基于当前在售与在团数据的推荐回答与可点击的商品卡片；LLM 不可用 SHALL 明确报错而非伪造推荐

## Impact

- **后端**：`application.yml`（ai 默认值/超时/thinking 开关）、`AiProperties`（thinking 字段）、`AiClient`（请求体带思考开关）、新增 `AiRecommendService` / `AiRecommendController` / `dto/AiRecommendVO`；`application-test.yml` 覆盖空 key 保证测试不外呼
- **前端**：`AiShoppingAssistant.vue` 两段式改造、新增 `api/ai.js`
- **测试**：`AiRecommendServiceTest`（mock LLM：候选组装、点名商品提取、不可用报错）；现有 96 用例不受影响（测试 profile 无 key）
- **文档**：README（AI 导购交互说明、key 配置说明）、`docs/verification-report.md` 追加真实 LLM 实测
