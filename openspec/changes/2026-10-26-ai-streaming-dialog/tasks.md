# Tasks

- [x] 1.1 AiClient：chatStream（逐段回调、仅 delta.content、完整正文返回）+ 多轮 chat(system, history, user) + parseStreamDelta 静态解析，验证：单测
- [x] 1.2 AiRecommendService：recommend/recommendStream 带 history（boundHistory 裁剪最近 4 条、角色与长度校验）；requireAvailable 供流式端点注册 emitter 前置校验
- [x] 1.3 Controller：POST /api/ai/recommend/stream（SSE：delta/done/error，独立线程池）；两端点接受 history
- [x] 1.4 前端：api/ai.js 流式消费（fetch + Authorization 头 + 可 abort）；AI 页打字机光标动画 + 「追问一句」输入区（history 前端持有，两轮上限）
- [x] 1.5 mvn test 102 全绿 + npm run build；浏览器实测：流式增量渲染（2.5s 空→5s 已 146 字）、追问理解上下文（"再便宜点"接住苹果语境给出 8.50 花牛与跨品类替代）
