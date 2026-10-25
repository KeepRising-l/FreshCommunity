# Proposal

## Why

站内消息目前是"拉"模式：未读数只在路由切换时刷新，事件本身要等用户自己进消息中心才看见——演示"成团提醒"时，用户付完款毫无反馈，得手动点进消息页才看到那条成团通知，实时感为零。此外核销成功只有视觉反馈（无提示音）、待支付倒计时最后 1 分钟仍按 30 秒粒度跳动（看着像卡死）。

## What Changes

- **SSE 实时推送**：新增 `GET /api/notifications/stream`（登录用户，长连接）。`NotificationService.notify` 在事务提交后向该用户的在线连接推送事件（消息全文 + 引用）；前端用 fetch 流消费（携带 JWT 头、避免 token 进 URL），收到事件即刷新未读角标，若正处消息中心则自动刷新列表。断线自动重连（指数退避），重连后拉一次未读数补偿丢事件。
- **核销成功提示音**：核销台成功/失败播放短促提示音（Web Audio 合成，无音频资产），移动端演示氛围感。
- **倒计时末段加速**：待支付订单剩余 <70 秒时倒计时切换为秒级跳动（全局 30 秒粒度不变，卡片组件局部加速）。
- **明确不做**：WebSocket 双向通信（单向推送 SSE 足够）、离线推送、消息中心虚拟滚动。

## Capabilities

### Modified Capabilities

- `notifications`: 新增「实时推送」Requirement——消息产生并提交后 SHALL 即时推送至在线用户；连接 SHALL 支持断线重连且不丢未读数口径
- `pickup-verification`: 核销结果新增提示音反馈 Scenario

## Impact

- **后端**：新增 `NotificationPushService`（SseEmitter 注册表 + 事务提交后推送 + 30s 心跳）；`NotificationController` 加 stream 端点；`NotificationService` 挂接推送；测试 profile 无特殊处理（SSE 不影响既有用例）
- **前端**：`utils/sse.js`（fetch 流解析）、`stores/notification.js` 增连接管理与事件回调、`Dashboard.vue` 挂载/卸载连接；`LeaderVerify.vue` 提示音；`OrderCard.vue` 末段秒级倒计时
- **测试**：`NotificationApiTest` 补 stream 端点鉴权用例（未登录 401）；推送逻辑以真实事务路径回归（成团/核销/订阅命中事件即推送）
