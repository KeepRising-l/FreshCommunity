# Tasks

- [x] 1.1 `AiRecommendVO.ItemVO` 加 `pickupPointName`；`AiRecommendService` 注入 `UserMapper`，批量取进行中团购的团长资料回填，验证：`mvn compile`
- [x] 1.2 前端：推荐卡片（有 groupBuyId）加「立即参团」按钮 → 确认弹窗（商品/数量 el-input-number/提货点/应付合计）→ `createOrder` → 成功提示带「去支付」（`payOrder`）与「去订单列表」，验证：`npm run build`
- [x] 1.3 浏览器实测：问 AI → 推荐卡片点参团 → 下单 → 支付 → 我的订单出现该笔（待成团/已成团），记录进 verification-report §十三
- [x] 1.4 `mvn test` 全量绿；README 接口说明补一句；归档变更
