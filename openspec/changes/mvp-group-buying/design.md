# Design

## Context

全新项目，无存量代码与数据。技术栈已定：Spring Boot + Vue + MySQL（见 proposal.md）。业务规则已在 7 份 spec 中确认（角色、团购、库存、订单、核销、看板）。本设计补充表结构、状态机与关键技术选择。

## Goals / Non-Goals

**Goals:**

- 单体 Spring Boot 应用 + Vue 单页应用，本地一键启动，满足毕设演示
- 订单状态机与团购判定逻辑正确，防超卖可靠
- 支付模拟但预留扩展点（PayService 接口）

**Non-Goals:**

- 不做微服务拆分、消息队列、分布式锁
- 不做真实支付、佣金结算、消息推送（见 proposal 不做清单）
- 不做移动端原生 App / 小程序

## Decisions

1. **框架组合**：Spring Boot 3 + MyBatis-Plus + MySQL 8；前端 Vue 3 + Vite + Element Plus + Axios。
   备选：JPA（写复杂扣减 SQL 不如 MyBatis 直观）、React（团队更熟 Vue 生态）。选国内资料多的组合，利于毕设写作与答辩。

2. **认证**：Session/Cookie 还是 JWT？选 JWT（Header 携带），前后端分离下最简单；角色用 Spring Security 的注解式鉴权（`@PreAuthorize`）。备选 Sa-Token（更轻，若 Spring Security 配置过重可替换，接口不变）。

3. **防超卖**：下单扣库存用数据库原子更新 `UPDATE product SET stock = stock - n WHERE id = ? AND stock >= n`，受影响行数为 0 即库存不足回滚。不引入 Redis 预扣（单机毕设场景收益小、复杂度高）。

4. **成团/解散/超时关单的判定**：Spring `@Scheduled` 定时任务，每分钟扫描：
   - 已支付人数 ≥ 成团人数 → 置"已成团"，批量生成取货码
   - 到截止时间未成团 → 置"已解散"，订单批量退款 + 回补库存
   - 待支付超 30 分钟 → 关单 + 回补库存
   - 提前成团兜底：支付成功的事务里同步检查是否达标，达标立即成团；定时任务只做兜底，保证即使支付并发达标也只成团一次（状态机 + 乐观条件更新保证幂等）。

5. **取货码**：成团时为每个订单生成 6 位数字随机码，唯一索引防撞；查询走索引，冲突重试。

6. **支付扩展点**：定义 `PayService` 接口（`pay` / `refund`），默认实现 `MockPayServiceImpl` 直接置状态。答辩时说明对接微信支付只需新增实现类。

7. **数据表**（核心 6 张）：
   - `user`（id, username, password, role, pickup_point_name, pickup_point_address）
   - `product`（id, name, price, stock, image_url, status）
   - `group_buy`（id, title, leader_id, target_count, deadline, status[进行中/已成团/已解散], member_count）
   - `group_buy_item`（id, group_buy_id, product_id, price_snapshot）
   - `order`（id, order_no, user_id, group_buy_id, product_id, quantity, amount, status[待支付/待成团/已成团/待取货/已完成/已取消/已退款], pickup_code, pay_time, created_at）
   - 简化决策：订单直接记单一商品与数量（团购按单品开），不做购物车。备选：多商品订单（引入订单明细表，前端复杂度上升）——MVP 砍掉，一次买多件 = 数量加多次。

## Risks / Trade-offs

- [定时任务分钟级扫描导致成团/解散有延迟] → 提前成团走支付事务内同步判定；解散容忍 ≤1 分钟延迟，演示无感
- [并发支付同时达标导致重复成团] → 成团状态变更用条件更新（`WHERE status='进行中'`），只允许一次成功
- [模拟支付与真实支付行为差异] → PayService 接口隔离，状态机与业务逻辑不感知支付实现
- [JWT 无法主动失效] → 毕设场景可接受；密码存 BCrypt 哈希
- [库存扣减与订单创建跨表事务] → 同一数据库本地事务保证一致性

## Migration Plan

全新项目无需迁移。落地顺序：建库表 → 后端骨架与登录鉴权 → 商品/团购/订单接口 → 定时任务 → 前端三角色页面 → 联调演示数据。

## Open Questions

无——所有业务规则已在访谈中确认。
