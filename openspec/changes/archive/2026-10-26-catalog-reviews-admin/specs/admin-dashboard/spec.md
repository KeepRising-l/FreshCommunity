# Spec Delta

## ADDED Requirements

### Requirement: 订单数据导出
管理员 SHALL 能将订单数据导出为 CSV（与订单管理页筛选口径一致），文件 SHALL 使用 UTF-8 BOM 编码保证 Excel 中文正常，含逗号/引号/换行的字段 SHALL 按 CSV 规范转义；非管理员 SHALL 无法导出。

#### Scenario: 导出当前筛选
- **WHEN** 管理员在订单管理页按状态筛选后点击导出
- **THEN** 下载的 CSV 仅含符合筛选的订单，Excel 打开中文不乱码

#### Scenario: 非管理员被拒
- **WHEN** 普通用户或团长请求导出接口
- **THEN** 系统拒绝该请求
