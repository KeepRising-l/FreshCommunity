# Proposal

## Why

AI 即时推荐虽已打通"问→推→买"，但回答是一次性整段返回（等 3-7 秒白屏），且只能问一句——用户想追问"再便宜点呢"只能重新描述完整需求。演示观感与真实 AI 助手差距明显。

## What Changes

- **流式输出**：`POST /api/ai/recommend/stream`（SSE）——正文逐段推送（delta 事件），前端打字机渲染，完成后 done 事件携带商品卡片；LLM 失败发 error 事件（不伪造）；思考内容（reasoning_content）不进入输出。AiClient 新增流式调用（网关已实测支持 SSE）与 SSE 行解析
- **追问（多轮上下文）**：同步与流式端点均接受 `history`（user/assistant 对，服务端裁剪最近 4 条防膨胀与注入）；前端首轮回答后出现「追问一句」输入框，最多两轮后引导重新开问
- **明确不做**：会话持久化（上下文仅存前端当次会话）、流式中断恢复

## Capabilities

### Modified Capabilities
- `ai-subscription`: 「AI 即时推荐」扩展流式输出与追问口径

## Impact
- 后端：`AiClient`（chatStream + 多轮 chat + parseStreamDelta）、`AiRecommendService`（recommend/recommendStream + boundHistory + requireAvailable）、`AiRecommendController`（stream 端点 + history 字段 + 独立线程池）
- 前端：`api/ai.js` 流式消费（fetch POST + SSE 解析 + 可中断）；AI 导购页打字机光标 + 追问区
- 测试：+2（parseStreamDelta 解析；流式端点无 key 前置 400），全量 102 绿
