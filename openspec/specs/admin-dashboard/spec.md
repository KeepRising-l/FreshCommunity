# admin-dashboard Specification

## Purpose

管理员首页数据看板，展示平台当日运营概况：今日订单数与今日成团数。

## Requirements

### Requirement: 今日订单数
管理员首页 SHALL 展示今日创建的订单总数。

#### Scenario: 查看今日订单数
- **WHEN** 管理员打开数据看板
- **THEN** 页面展示当日 0 点至当前时间创建的订单总数

### Requirement: 今日成团数
管理员首页 SHALL 展示今日成团的团购总数。

#### Scenario: 查看今日成团数
- **WHEN** 管理员打开数据看板
- **THEN** 页面展示当日判定为"已成团"的团购总数
