# Tasks

## 1. 真实图片素材

- [x] 1.1 新建 `scripts/fetch-seed-images.ps1`：按 17 商品 + 3 提货点的关键词表调 Commons 搜索 API 下载 640px 图（JPEG/PNG 校验、≥10KB 阈值、失败列出清单），落 `frontend/public/images/products/p01..17.jpg` 与 `frontend/public/images/pickup/pickup1..3.jpg`，验证：20 张文件全部为有效图片且体积阈值达标
- [x] 1.2 `seed.sql`：17 个商品 `image_url` 写 `/images/products/pNN.jpg`；3 个团长 `pickup_point_image_url` 写对应 `/images/pickup/…`，验证：重导 seed 后商品列表接口全部返回非空 `imageUrl`
- [x] 1.3 `.gitignore` 确认图片目录不入忽略清单，验证：`git status` 能看到新素材文件

## 2. 数据库与后端

- [x] 2.1 `db/schema.sql`：`user` 表加 `avatar_url VARCHAR(255)`、`pickup_point_image_url VARCHAR(255)`；新增 `db/migrate-2026-10-25-images.sql`（含注释），验证：重建库后 DESC user 含两列
- [x] 2.2 `User` 实体与 `UserVO` 补 `avatarUrl` / `pickupPointImageUrl`，验证：`/api/auth/me` 响应含 `avatarUrl` 字段
- [x] 2.3 `PUT /api/auth/profile` 扩展：`avatarUrl` 全角色可写（校验 URL 前缀 `/uploads/` 或 `/images/`），提货点三字段（名称/地址/照片）仍仅团长；团长响应带回全部新字段，验证：user1 仅改头像成功、user1 改提货点仍 400、leader1 三项全改成功
- [x] 2.4 `FileController` 移除 ADMIN 限制（`isAuthenticated`），白名单/大小不变，验证：user1 上传 PNG 200、未登录 401
- [x] 2.5 `GroupBuyListVO` / `GroupBuyDetailVO` 加 `pickupPointImageUrl`，`GroupBuyController` 两处填充团长资料时带出，验证：团购详情响应含 `pickupPointImageUrl`

## 3. 前端

- [x] 3.1 `vite.config.js` 加 `/uploads` 代理，验证：上传返回的 `/uploads/...` URL 在 5173 下可加载
- [x] 3.2 `Profile.vue`：头像卡（当前头像 + 上传按钮，全角色）与团长提货点照片上传（el-upload → `/api/upload` → profile 落库），更新后同步 `stores/user`，验证：上传头像后侧边栏头像即时变化
- [x] 3.3 `Dashboard.vue`：侧边栏与顶栏头像 `avatarUrl` 有图显示图、无图回落首字母，验证：设置头像后两处一致
- [x] 3.4 `GroupBuyDetail.vue` 提货点卡有照片时展示（无图维持现状），`utils/visual.js` 注释更新为"兜底"定位，验证：详情页渲染
- [x] 3.5 `npm run build` 通过，验证：`dist` 内含 `images/` 素材目录

## 4. 测试、验证与文档

- [x] 4.1 更新 `UploadAndSecurityApiTest.uploadRequiresAdmin` → 登录用户可上传（未登录仍 401）；`ProfileApiTest` 补：user1 改头像成功且 `/me` 带出、user1 提交提货点字段仍 400、leader1 提货点照片落库；非法 URL 前缀被拒；验证：`mvn test` 全量绿
- [x] 4.2 端到端实测：重建库 → 起后端 → 断言商品/团长种子图 URL、上传、profile 落库、详情带图；浏览器 390px 与桌面各截图确认真实图片渲染；追加 `docs/verification-report.md` §十一
- [x] 4.3 同步 `README.md`（图片说明、素材来源与授权、上传权限、接口表 upload 行）与 `docs/implementation-inventory.md`（FR-2-4 / FR-10 更新、计数），验证：口径一致
- [x] 4.4 归档变更并 `openspec validate --specs` 通过，验证：user-roles 基线含新 Requirement
