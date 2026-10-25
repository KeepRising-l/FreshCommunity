# Tasks

## 1. 底部导航

- [x] 1.1 `Dashboard.vue`：新增 `.tabbar` 底部导航（≤768px 显示，桌面隐藏），按角色分配 ≤5 个入口（用户=首页/团购广场/我的订单/消息/我的；团长=首页/团购广场/我的团购/核销台/我的；管理员=首页/数据看板/商品管理/订单管理/我的），当前项高亮，消息项未读角标，`env(safe-area-inset-bottom)` 内边距，验证：浏览器 390px 视口三角色入口正确且高亮随路由切换
- [x] 1.2 窄屏隐藏侧边栏、内容区预留 tabbar 高度（含 safe-area），顶栏收起面包屑根与搜索框，验证：390px 下无横向滚动条

## 2. 详情页固定操作栏与全局移动层

- [x] 2.1 `GroupBuyDetail.vue`：≤768px 显示底部固定栏（拼团价 + 参团按钮，复用 `openJoin` 与禁用条件），内容区预留高度；桌面隐藏，验证：390px 滚动到底部时操作栏仍可见
- [x] 2.2 `tokens.css` 移动层：≤768px 下 `el-dialog`/`el-message-box` 统一 92% 宽度；默认按钮 `min-height: 44px`（small 按钮例外 38px，保表格密度），验证：390px 打开参团弹窗不溢出、按钮可点
- [x] 2.3 `npm run build` 通过，验证：退出码 0、无新增依赖

## 3. 验证与文档

- [x] 3.1 浏览器实测（390×844 视口）：登录 → Tab 切换 → 团购详情固定栏 → 参团弹窗 → 消息中心，桌面宽度回归（侧边栏布局不变），验证：截图与断言记录进 verification-report
- [x] 3.2 同步 `docs/implementation-inventory.md`（FR-10-5 转 ✅）与 README（一句话），验证：口径一致
- [x] 3.3 归档变更并 `openspec validate --specs` 通过，验证：基线含 responsive-ui capability
