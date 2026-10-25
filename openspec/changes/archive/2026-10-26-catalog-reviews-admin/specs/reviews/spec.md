# Spec Delta

## ADDED Requirements

### Requirement: 团长回复评价
团长 SHALL 能对自己收到的评价进行一层回复（可修改，≤500 字）；其他团长与普通用户 SHALL 无法回复；回复 SHALL 在商品评价列表与团购详情的评价区展示。

#### Scenario: 团长回复并修改
- **WHEN** 被评团长对一条评价提交回复后再次修改
- **THEN** 评价展示最新回复内容与更新时间

#### Scenario: 越权回复被拒
- **WHEN** 非该评价所属团长请求回复
- **THEN** 系统拒绝该请求

#### Scenario: 回复公开展示
- **WHEN** 买家或其他用户查看该商品评价
- **THEN** 可见评价原文与团长回复
