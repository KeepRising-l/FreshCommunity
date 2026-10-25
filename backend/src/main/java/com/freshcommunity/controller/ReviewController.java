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

    /** 团长本人名下的评价列表（评价回复页数据源）。 */
    @GetMapping("/leader/mine")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('LEADER')")
    public Result<List<com.freshcommunity.dto.LeaderReviewVO>> leaderMine(
            @RequestAttribute("userId") Long leaderId) {
        return Result.success(reviewService.listByLeader(leaderId));
    }

    /** 团长回复评价（一层，可修改；仅被评团长本人）。 */
    @PostMapping("/{id}/reply")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('LEADER')")
    public Result<com.freshcommunity.dto.LeaderReviewVO> reply(
            @RequestAttribute("userId") Long leaderId,
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body) {
        return Result.success(reviewService.reply(leaderId, id, body.get("content")));
    }
}
