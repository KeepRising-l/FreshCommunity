# Proposal

## Why

四个"想清楚了可以不做、做了更完整"的收尾项，一次交付：商品只有单图（详情页无图库感）；买家评价团长无法回应（差评没解释渠道，好评没互动）；管理员看板数据没法导出（毕设文档常要求附数据表）；商品详情页看完即走（无同分类推荐承接）。

## What Changes

- **商品多图**：新增 `product_image` 表（product_id + url + sort，首图即 `product.image_url` 冗余为列表主图）；管理端商品表单支持多图上传与排序（首图为列表/卡片用图）；商品详情返回 `images` 列表并渲染图库（主图 + 缩略图切换）；既有 imageUrl 消费方（列表卡/团购卡/订单卡）不变
- **团长回复评价**：`review` 表加 `leader_reply` / `leader_replied_at`；新增 `GET /api/reviews/leader/mine`（本人名下评价列表，含商品名与订单号）与 `POST /api/reviews/{id}/reply`（仅被评团长本人，≤500 字，可修改）；新增团长「评价回复」页；商品详情与团购详情的评价列表展示"团长回复"气泡
- **看板 CSV 导出**：`GET /api/admin/orders/export?status=&keyword=`（当前筛选口径，上限 5000 行，UTF-8 BOM 防 Excel 乱码）；订单管理页「导出 CSV」按钮
- **同分类推荐**：`GET /api/products/{id}/similar?size=4`（同分类在售、排除自身、按评分高→创建新排序）；商品详情底部「看了又看」网格
- **明确不做**：图片水印/压缩、回复再回复（一层即止）、Excel(xlsx) 导出、个性化推荐算法

## Capabilities

### Modified Capabilities
- `product-catalog`: 商品多图（图库口径）与同分类推荐
- `reviews`: 团长回复评价（一层回复、仅本人）
- `admin-dashboard`: 订单数据 CSV 导出

## Impact
- 数据库：新表 `product_image`、`review` 加两列；schema/seed 同步 + `db/migrate-2026-10-26-catalog.sql`；seed 为 3 个明星商品补第二张图
- 后端：`ProductImage` 实体/Mapper、`ProductController`（detail 带 images、similar、admin create/update 维护图集）、`Review` 实体 + `ReviewController`（reply/mine）+ `ReviewVO`、`AdminController` export
- 前端：`AdminProducts`（多图上传列表）、`ProductDetail`（图库 + 看了又看）、`LeaderReviews.vue` 新页 + 菜单、`GroupBuyDetail`/`ProductDetail` 评价回复气泡、`AdminOrders` 导出按钮
- 测试：多图维护/读取、similar 口径、回复权限与展示、导出（BOM/表头/筛选一致）
