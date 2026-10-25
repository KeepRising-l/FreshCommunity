# Tasks

- [x] 1.1 数据库：product_image 表（sort 序、首图冗余 product.image_url）+ review 加 leader_reply/leader_replied_at；schema/seed/migrate-2026-10-26-catalog.sql；seed 为草莓/红富士/花牛补第二张图
- [x] 1.2 后端商品：detail 填充 images（空回落单图）、similar（同分类在售排除自身，size≤8）、admin create/update 替换式保存图集 + 首图回写
- [x] 1.3 后端评价：LeaderReviewVO + listByLeader SQL；reply（仅被评团长、≤500 字、可修改）；ReviewVO 带回复字段（r.* 自动映射）
- [x] 1.4 后端导出：GET /api/admin/orders/export（同筛选口径、5000 行上限、BOM、CSV 转义、RFC5987 文件名）
- [x] 1.5 前端：AdminProducts 多图上传条（封面标记/前后移/删除）；ProductDetail 图库（缩略图切换）+ 看了又看 + 回复气泡；GroupBuyDetail 回复气泡；LeaderReviews 新页（回复/修改编辑器）+ 路由守卫 + 菜单；AdminOrders 导出按钮（fetch 带 token 下载）
- [x] 1.6 测试：CatalogReviewsAdminTest 4 用例（多图维护/同分类口径/回复权限与展示/CSV BOM+转义+筛选）全绿（总量 106）；build 通过
- [x] 1.7 端到端实测：草莓图集 2 图、同类推荐 4 个水果、CSV BOM 表头数据行、团长回复发布后商品评价列表带出、user1 访问 /leader/reviews 被角色守卫拦截
