# Tasks

- [x] 1.1 后端：`NotificationPushService`（ConcurrentHashMap<userId, emitters>；register/unregister/heartbeat 30s；push(userId, eventJSON)；仅事务提交后推送：notify 落库后经 TransactionSynchronization afterCommit 触发，无事务时立即），验证：`mvn compile`
- [x] 1.2 `NotificationController` 加 `GET /api/notifications/stream`（登录，返回 SseEmitter，超时 0 无限 + 注册即送 hello 事件）；`NotificationService.notify` 挂接推送，验证：MockMvc 集成——未登录 401；成团事务后 emitter 收到事件（用 MockMvc async 或直接单测 PushService）
- [x] 1.3 前端 `utils/sse.js`：fetch POST/GET 流式读取 + `data:` 行解析 + 指数退避重连（1s/2s/4s…封顶 30s），重连成功回调刷新未读数；`stores/notification.js` 管理 connect/close/事件回调；`Dashboard.vue` onMounted 连接、onUnmounted 关闭；收到事件 → 未读数刷新 + 当前在消息中心则重载列表
- [x] 1.4 `LeaderVerify.vue`：成功/失败提示音（Web Audio 合成短音，无资产），验证：浏览器实测核销有声音
- [x] 1.5 `OrderCard.vue`：PENDING_PAYMENT 且剩余 <70s 时局部 1s 倒计时（全局 30s 粒度不变），验证：浏览器实测末段秒跳
- [x] 1.6 `mvn test` 全量绿 + `npm run build`；浏览器实测：两个标签页，一个下单支付触发成团，另一个未读角标秒级 +1，记录 verification-report §十四；归档提交
