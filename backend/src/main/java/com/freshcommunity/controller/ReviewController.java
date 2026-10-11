package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.dto.ProductReviewsVO;
import com.freshcommunity.dto.ReviewCreateRequest;
import com.freshcommunity.entity.Review;
import com.freshcommunity.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public Result<Review> submit(@RequestAttribute("userId") Long userId,
                                 @Valid @RequestBody ReviewCreateRequest req) {
        return Result.success(reviewService.submit(userId, req));
    }

    @GetMapping("/mine")
    public Result<List<Review>> mine(@RequestAttribute("userId") Long userId) {
        return Result.success(reviewService.listByUser(userId));
    }

    @GetMapping("/product/{productId}")
    public Result<ProductReviewsVO> productReviews(@PathVariable Long productId) {
        return Result.success(new ProductReviewsVO(
                reviewService.productRating(productId),
                reviewService.listVOByProduct(productId)));
    }
}
