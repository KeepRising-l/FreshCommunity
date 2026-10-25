# Proposal

## Why

平台自上线以来商品图一直是「关键词 → emoji + 渐变」的占位方案（`utils/visual.js` 自述为补齐图片能力前的临时方案），演示与答辩时观感明显单薄；同时上传接口仅管理员可用，团长与普通用户没有任何上传真实图片的入口——团长无法上传提货点照片，任何人都没有头像。

本变更把图片能力做实：**真实照片素材入种子数据**（17 个商品 + 3 个提货点，取自 Wikimedia Commons 免费授权图库，仓库内置、离线可用），**上传能力开放到所有登录用户**（头像 / 团长提货点照片），占位方案降级为"无图时的兜底"。

## What Changes

- **真实商品图**：`db/seed-images/products/` 内置 17 张按商品关键词检索的真实照片（Commons 免费授权，640px），`db/seed-images/pickup/` 内置 3 张提货点照片；前端 `public/images/` 构建期自动携带（`scripts/fetch-seed-images.ps1` 一键刷新），`seed.sql` 商品与团长行写入对应 `image_url`
- **上传能力开放**：`POST /api/upload` 从仅管理员改为所有登录用户可用（类型白名单与 5MB 上限不变）；新增头像与提货点照片两个落点
- **头像（全角色）**：`user` 表加 `avatar_url`；`PUT /api/auth/profile` 接受 `avatarUrl`（所有角色）；个人中心支持上传头像并即时预览；侧边栏 / 顶栏头像显示真实图片（无图回落首字母）
- **提货点照片（团长）**：`user` 表加 `pickup_point_image_url`；`PUT /api/auth/profile` 团长可维护；团购详情的提货点卡片展示照片
- **代理修复**：Vite 增加 `/uploads` 代理——修复已上传图片（`/uploads/**` 由后端静态服务）在前端开发服务器 404 的既有缺陷
- **明确不做**：图片压缩/裁剪、对象存储/CDN、用户相册、商品多图（保持单图）、图片审核

## Capabilities

### Modified Capabilities

- `user-roles`: 「团长维护提货点」扩展为「头像与提货点照片维护」——头像全角色可维护（经上传接口），提货点名称/地址/照片仍仅团长；上传接口从管理员专属开放到全部登录用户

## Impact

- **数据库**：`user` 表 `ADD COLUMN avatar_url VARCHAR(255)`、`pickup_point_image_url VARCHAR(255)`；`db/schema.sql` / `seed.sql` 同步 + 新增 `db/migrate-2026-10-25-images.sql`
- **后端**：`User` 实体与 `UserVO` 补字段；`AuthController.updateProfile` 扩展（头像全角色 / 提货点照片团长）；`FileController` 放开角色；`GroupBuyListVO` / `GroupBuyDetailVO` 补 `pickupPointImageUrl`（从团长资料带出）
- **前端**：`Profile.vue`（头像 + 提货点照片上传）、`Dashboard.vue`（真实头像渲染）、`GroupBuyDetail.vue`（提货点照片）；`vite.config.js` 加 `/uploads` 代理；`utils/visual.js` 注释更新（占位降级为兜底）
- **资产**：新增 `frontend/public/images/`（20 张真实照片，约 1–2MB）与刷新脚本；`.gitignore` 调整（图片入仓）
- **测试**：上传权限用例改写（用户可传）、头像/提货点照片维护用例；现有 94 用例须全绿
