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
    private final ReviewService reviewService;
    private final ReviewAnalysisService reviewAnalysisService;

    public ProductController(ProductMapper productMapper, ReviewService reviewService,
                             ReviewAnalysisService reviewAnalysisService) {
        this.productMapper = productMapper;
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
        return Result.success(p);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@Valid @RequestBody Product product) {
        normalizeCategory(product);
        product.setStatus(1);
        productMapper.insert(product);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Product product) {
        normalizeCategory(product);
        product.setId(id);
        productMapper.updateById(product);
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

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
