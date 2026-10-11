package com.freshcommunity.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.common.Result;
import com.freshcommunity.dto.RatingVO;
import com.freshcommunity.entity.Product;
import com.freshcommunity.mapper.ProductMapper;
import com.freshcommunity.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductMapper productMapper;
    private final ReviewService reviewService;

    public ProductController(ProductMapper productMapper, ReviewService reviewService) {
        this.productMapper = productMapper;
        this.reviewService = reviewService;
    }

    @GetMapping
    public Result<List<Product>> list() {
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getCreatedAt));
        return Result.success(list);
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
        return Result.success(p);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@Valid @RequestBody Product product) {
        product.setStatus(1);
        productMapper.insert(product);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Product product) {
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
}
