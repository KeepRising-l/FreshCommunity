# responsive-ui Specification

## Purpose
TBD - created by archiving change 2026-10-25-mobile-adaptation. Update Purpose after archive.

## Requirements

### Requirement: 窄屏底部导航
视口宽度 ≤768px 时，系统 SHALL 以底部导航栏（≤5 个入口，按登录角色分配）替代桌面侧边栏作为主导航，当前路由对应项 SHALL 高亮，消息入口 SHALL 展示未读数角标；桌面宽度（>768px）SHALL 保持侧边栏布局不变。

#### Scenario: 用户角色导航
- **WHEN** 普通用户在手机宽度下登录
- **THEN** 底部导航包含首页、团购广场、我的订单、消息中心与个人中心，且消息项显示未读角标

#### Scenario: 团长与管理员导航
- **WHEN** 团长（或管理员）在手机宽度下登录
- **THEN** 底部导航包含其角色核心入口（团长的我的团购与核销台、管理员的数据看板与商品/订单管理）

#### Scenario: 桌面布局不受影响
- **WHEN** 视口宽度大于 768px
- **THEN** 页面保持桌面侧边栏布局，不出现底部导航

### Requirement: 窄屏核心操作可达
窄屏下，团购详情页 SHALL 将拼团价与参团操作固定在底部可视区域（滚动时不消失），参团弹窗 SHALL 不超出视口宽度；默认按钮的触摸目标 SHALL 不小于 44px 高（表格内 small 按钮除外）。

#### Scenario: 固定参团操作栏
- **WHEN** 用户在手机宽度下浏览团购详情并滚动页面
- **THEN** 拼团价与参团按钮始终固定在底部可见

#### Scenario: 弹窗不溢出
- **WHEN** 用户在手机宽度下打开任意弹窗（如参团确认）
- **THEN** 弹窗宽度不超过视口（约 92%），内容完整可见
