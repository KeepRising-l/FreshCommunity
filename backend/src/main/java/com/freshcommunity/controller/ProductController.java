package com.freshcommunity.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshcommunity.common.PageResult;
import com.freshcommunity.common.Result;
import com.freshcommunity.dto.RatingVO;
import com.freshcommunity.entity.Product;
import com.freshcommunity.mapper.ProductMapper;
import com.freshcommunity.service.ReviewAnalysisService;
import com.freshcommunity.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    /** 分页大小上限：防止 size 传大值变相一次拉全量 */
    private static final int MAX_SIZE = 50;

    /** 排序白名单：排序值最终影响 orderBy 拼装，白名单杜绝注入面 */
    private static final Set<String> SORT_WHITELIST = Set.of("latest", "price_asc", "price_desc");

    private final ProductMapper productMapper;
    private final com.freshcommunity.mapper.ProductImageMapper productImageMapper;
    private final ReviewService reviewService;
    private final ReviewAnalysisService reviewAnalysisService;

    public ProductController(ProductMapper productMapper,
                             com.freshcommunity.mapper.ProductImageMapper productImageMapper,
                             ReviewService reviewService,
                             ReviewAnalysisService reviewAnalysisService) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.reviewService = reviewService;
        this.reviewAnalysisService = reviewAnalysisService;
    }

    /**
     * 在售商品检索：keyword 按名称模糊、category 等值、sort 白名单（默认最新上架）、page/size 分页。
     * 空白参数视为不筛，非法排序值回落 latest。
     */
    @GetMapping
    public Result<PageResult<Product>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false, defaultValue = "latest") String sort,
                                            @RequestParam(required = false, defaultValue = "1") long page,
                                            @RequestParam(required = false, defaultValue = "12") long size) {
        String kw = trimToNull(keyword);
        String cat = trimToNull(category);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .like(kw != null, Product::getName, kw)
                .eq(cat != null, Product::getCategory, cat);
        applySort(wrapper, sort);

        return Result.success(PageResult.from(productMapper.selectPage(pageOf(page, size), wrapper)));
    }

    /** 在售商品的分类去重列表（升序），供前端筛选 chips——与数据一致，不会筛出空结果。 */
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .select(Product::getCategory)
                        .eq(Product::getStatus, 1)
                        .groupBy(Product::getCategory)
                        .orderByAsc(Product::getCategory))
                .stream().map(Product::getCategory).toList());
    }

    /** 管理端全量列表：含下架商品，支持 keyword / status 筛选与分页（商品管理页数据源）。 */
    @GetMapping("/admin-list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<Product>> adminList(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false, defaultValue = "1") long page,
                                                 @RequestParam(required = false, defaultValue = "10") long size) {
        String kw = trimToNull(keyword);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(kw != null, Product::getName, kw)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreatedAt);
        return Result.success(PageResult.from(productMapper.selectPage(pageOf(page, size), wrapper)));
    }

    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        RatingVO rating = reviewService.productRating(id);
        p.setAvgScore(rating.getAvgScore());
        p.setReviewCount(rating.getCount());
        p.setInsightTags(reviewAnalysisService.topTags(id, 5));
        fillImages(p);
        return Result.success(p);
    }

    /** 同分类推荐：在售、排除自身，按评分高→新建在前，取 size 条（默认 4）。 */
    @GetMapping("/{id}/similar")
    public Result<List<Product>> similar(@PathVariable Long id,
                                         @RequestParam(required = false, defaultValue = "4") int size) {
        Product self = productMapper.selectById(id);
        if (self == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        int limit = Math.min(Math.max(size, 1), 8);
        List<Product> same = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .eq(Product::getCategory, self.getCategory())
                .ne(Product::getId, id)
                .orderByDesc(Product::getCreatedAt));
        if (same.size() > limit) {
            same = same.subList(0, limit);
        }
        return Result.success(same);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@Valid @RequestBody Product product) {
        normalizeCategory(product);
        syncFirstImage(product);
        product.setStatus(1);
        productMapper.insert(product);
        saveImages(product.getId(), product.getImages());
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Product product) {
        normalizeCategory(product);
        product.setId(id);
        syncFirstImage(product);
        productMapper.updateById(product);
        saveImages(id, product.getImages());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        productMapper.deleteById(id);
        return Result.success();
    }

    // ---------- 私有辅助 ----------

    private void applySort(LambdaQueryWrapper<Product> wrapper, String sort) {
        switch (SORT_WHITELIST.contains(sort) ? sort : "latest") {
            case "price_asc" -> wrapper.orderByAsc(Product::getPrice);
            case "price_desc" -> wrapper.orderByDesc(Product::getPrice);
            default -> wrapper.orderByDesc(Product::getCreatedAt);
        }
    }

    private Page<Product> pageOf(long page, long size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_SIZE));
    }

    /** 分类为空时落默认「其他」，非空时去首尾空白 */
    private void normalizeCategory(Product product) {
        String c = trimToNull(product.getCategory());
        product.setCategory(c == null ? "其他" : c);
    }

    /** 详情图集：product_image 按 sort 升序；空表回落单图 [imageUrl]。 */
    private void fillImages(Product p) {
        List<String> images = productImageMapper.selectList(
                        new LambdaQueryWrapper<com.freshcommunity.entity.ProductImage>()
                                .eq(com.freshcommunity.entity.ProductImage::getProductId, p.getId())
                                .orderByAsc(com.freshcommunity.entity.ProductImage::getSort))
                .stream().map(com.freshcommunity.entity.ProductImage::getImageUrl).toList();
        if (images.isEmpty() && p.getImageUrl() != null && !p.getImageUrl().isBlank()) {
            images = List.of(p.getImageUrl());
        }
        p.setImages(images);
    }

    /** 首图一致性：图集非空时把 image_url 回写为首图（列表/卡片消费方只看 image_url）。 */
    private void syncFirstImage(Product product) {
        List<String> images = product.getImages();
        if (images != null && !images.isEmpty()) {
            String first = images.get(0);
            if (first != null && !first.isBlank()) {
                product.setImageUrl(first.trim());
            }
        }
    }

    /** 替换式保存图集：全删后按序插入；images 为 null 时不动（兼容旧客户端只改文字）。 */
    private void saveImages(Long productId, List<String> images) {
        if (images == null) {
            return;
        }
        productImageMapper.delete(new LambdaQueryWrapper<com.freshcommunity.entity.ProductImage>()
                .eq(com.freshcommunity.entity.ProductImage::getProductId, productId));
        int sort = 0;
        for (String url : images) {
            if (url == null || url.isBlank()) {
                continue;
            }
            com.freshcommunity.entity.ProductImage img = new com.freshcommunity.entity.ProductImage();
            img.setProductId(productId);
            img.setImageUrl(url.trim());
            img.setSort(sort++);
            productImageMapper.insert(img);
        }
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
