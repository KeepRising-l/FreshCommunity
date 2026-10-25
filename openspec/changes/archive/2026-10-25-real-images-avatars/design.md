# Design

## Context

- `POST /api/upload`（ADMIN，扩展名+contentType 白名单，UUID 落盘 `${user.dir}/uploads`）已在上一轮加固；前端 `api/upload.js` 已封装。
- 商品图渲染链路已就绪：`Product`/`GroupBuyListVO`/`OrderVO` 都带 `imageUrl`，`GroupBuyCard` / `ProductList` / `ProductDetail` / `OrderCard` 有图优先、无图回落 emoji 占位——**只差真实图片数据与上传入口**。
- Vite 仅代理 `/api`，`/uploads/**`（后端静态服务）在前端 5173 下 404——上一轮接通上传后遗留的显示断点。
- 图片来源：Wikimedia Commons（免费授权，`Special:FilePath` 与搜索 API 稳定），离线演示需图片随仓库走。

## Goals / Non-Goals

**Goals:**

- 演示数据里的商品与提货点全部有真实照片，且仓库内置（克隆即得、离线可用）
- 任何登录用户可上传图片；头像全角色可换，提货点照片团长可换
- 上传的图片在各处真实显示（头像、提货点、商品），无图时占位兜底
- 修复 `/uploads` 在前端开发服务器的 404

**Non-Goals:**

- 不做图片压缩/裁剪/水印、不接对象存储、不做商品多图与图片审核
- 不为 seed 用户种子化头像（头像由用户上传演示，占位首字母本就是有效兜底）

## Decisions

### D1 真实素材放前端 `public/images/`，经构建分发

`seed.sql` 的 `image_url` 写 `/images/products/p01.jpg` 这类**前端静态路径**：

- 开发态由 Vite 直接服务，构建后随 `dist/` 分发，离线可用、零后端配置；
- 对比放后端 `uploads/`：uploads 在 `.gitignore`（运行时上传的用户内容），素材入仓需要独立目录与拷贝步骤，且绕不开 `/uploads` 代理——不如前端静态路径直接。

素材获取：`scripts/fetch-seed-images.ps1` 调 Wikimedia Commons 搜索 API 按英文关键词取 640px 缩略图（JPEG/PNG 校验 + 最小体积阈值），17 商品 + 3 提货点；下载结果**提交入仓**，脚本仅用于刷新/补图。授权：Commons 均为自由许可，毕设演示合规，README 附来源说明。

### D2 上传接口放开到所有登录用户，落点职责区分

`POST /api/upload` 移除 ADMIN 限制（`isAuthenticated`）：白名单校验与 UUID 文件名已保证安全，放开后头像/提货点照片/商品图共用一条通道。滥用面（毕设内网演示）可接受，README 注明生产需按用户限频。

字段维护职责：`PUT /api/auth/profile` 请求体扩展为 `{avatarUrl?, pickupPointName?, pickupPointAddress?, pickupPointImageUrl?}`——`avatarUrl` 全角色可写；三个提货点字段仍仅团长可写（普通用户提交提货点字段依旧拒绝，保持既有规格语义）。上传得到 URL、再经 profile 落库，两步分离：上传接口不关心业务语义，profile 做字段级校验（URL 必须以 `/uploads/` 或 `/images/` 开头，防塞外链脚本）。

### D3 头像与提货点照片的读取链路

- `UserVO` 加 `avatarUrl`：`/api/auth/me`、登录、注册响应自动带出；`stores/user.js` 的 `user` 对象随登录写入——侧边栏/顶栏直接读 store，Profile 更新后同步 `setUser` 保持一致。
- `GroupBuyListVO` / `GroupBuyDetailVO` 加 `pickupPointImageUrl`：列表与详情填充团长资料处（`GroupBuyController`）顺带带出，团购详情提货点卡有图显示图、无图维持现状。

### D4 Vite `/uploads` 代理

`vite.config.js` 增加 `'/uploads': { target: 'http://localhost:8080' }`。开发态上传图（用户头像、管理端上传的商品图）即可显示；生产部署时前端与 `/uploads` 同域或反代，属部署配置。

### D5 占位方案降级为兜底

`utils/visual.js` 的 emoji+渐变保留（商品被删、历史订单商品名缺失、用户未传头像等场景仍是有效兜底），仅更新注释与定位说明：有图显示图，无图才回落。

## Risks / Trade-offs

- **Commons 检索的首图不一定最贴切**：素材以"可辨识"为验收线，下载脚本带关键词人工可调、可重跑；个别不满意的图可在管理端直接上传替换（这本身就是本轮交付的能力）。
- **图片入仓增加仓库体积（约 1–2MB）**：毕设规模完全可接受，换来离线演示稳定性。
- **上传放开后的磁盘增长**：内网演示无风险；README 注明生产需限频与配额。
