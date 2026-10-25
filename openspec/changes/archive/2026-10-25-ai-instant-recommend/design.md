# Design

## Context

- 现有 `AiClient`：OpenAI 兼容 chat/completions 轻量封装，无 key 时 `isAvailable()=false`，异常统一返 null；`AiProperties`（baseUrl/apiKey/model/timeoutSeconds）。
- 现有 AI 导购页只有订阅流；`LlmParseService`（LLM 优先 + 规则兜底）服务于订阅解析。
- 用户提供的网关实测：`deepseek-v4.1-flash` 可用，2-3 秒返回；`reasoning_content` 独立字段输出思考、`content` 为最终答案；`chat_template_kwargs.thinking=false` 等关闭参数被网关接受但不透传生效。

## Goals / Non-Goals

**Goals:**

- 用户一句话需求 → 即时得到基于真实在售/在团数据的推荐回答 + 可点击商品卡片
- 推荐必须基于候选清单的真实数据（价格/库存/口碑），LLM 不编造
- LLM 不可用时明确报错，不做假推荐
- 思考模式：请求侧按标准方式关闭（将来网关支持即生效），正文不受影响

**Non-Goals:** 不做多轮对话、流式输出、会话记忆；不改订阅匹配逻辑本身。

## Decisions

### D1 候选清单在 Java 侧组装成紧凑文本，价格口径只用清单

每次推荐实时组装：在售商品（41 个）逐行 `#id 名称[分类] 零售价X 库存Y｜团购:「标题」拼团价Z 已参团m/n 剩H小时 提货点:…｜口碑:a,b`。LLM 只能引用清单里的编号/名称/价格——system prompt 明令"不要编造清单外信息、价格只用清单数字"。选文本而非 JSON 喂给模型：省 token、小模型对清单文本的引用更稳。

### D2 被点名商品的提取用"名称回扫"，不用让 LLM 再吐 JSON

回答生成后，后端把 41 个商品名按名称长度倒序在 `reply` 里做包含匹配（长名优先，避免"苹果"误配"红富士苹果"已点名时的短名抢占——顺序取先出现者，最多 6 个），命中商品映射为卡片：有进行中团购的挂团购（跳团购详情），否则挂商品（跳商品详情）。LLM 输出结构不可控，回扫是最稳的锚点；名称来自库内数据，误配率低（商品名互为包含的仅苹果系——长名优先已处理）。

### D3 失败语义：明确报错，不 mock

`AiClient` 返 null（超时/异常/未配 key）时 `recommend` 抛 `IllegalArgumentException("AI 服务暂时不可用，请稍后重试")`（HTTP 400，前端展示可重试）——响应里绝不出现伪造的推荐文本。无 key 环境订阅解析回落规则不变（既有行为），但即时推荐没有"规则版推荐"（那等于关键词搜索的伪 AI），宁可明确不可用。

### D4 思考模式开关进 AiClient 请求体

`AiProperties` 新增 `thinkingEnabled`（默认 false），为 false 时请求体带 `chat_template_kwargs: {"thinking": false}`（vLLM/SGLang 部署 DeepSeek 的标准关闭方式）。当前网关接受但不透传——思考内容进 `reasoning_content` 独立字段，`AiClient` 只取 `content`，正文与延迟（实测 2-3s）不受影响；将来网关支持该开关即自动生效。

### D5 key 与网关作为默认值写入 application.yml

用户明确要求落地使用该服务：`ai.base-url`/`model` 默认值改为网关与 `deepseek-v4.1-flash`，`ai.api-key` 默认值写入提供的 key，环境变量 `AI_API_KEY` 可覆盖（README 注明生产建议轮换）。`application-test.yml` 覆盖 `ai.api-key: ""`——集成测试环境回到"无 key"状态，既有 96 用例不受影响、不会外呼。

### D6 推荐结果与订阅的衔接

推荐回答下方提供"把该需求转为订阅提醒"入口：前端把同一段需求文本走既有 `POST /api/subscriptions/parse → POST /api/subscriptions` 流程，即时推荐（现在的答案）与订阅（将来的提醒）在页面上形成互补闭环。

## Risks / Trade-offs

- **key 入库**：毕设演示以可用性优先；README 明示生产用环境变量覆盖并轮换。
- **LLM 输出不稳**：提示词约束三段式 + 150 字上限；回扫提取失败（模型没点名任何商品）时 items 为空，前端只展示文本回答——不阻塞主流程。
- **每次推荐都是一次 LLM 调用**（约 2-4s、41 商品候选约 2KB token）：毕设演示频次下无成本压力；README 注明生产需加限频。
