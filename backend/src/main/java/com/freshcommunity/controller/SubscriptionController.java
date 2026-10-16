package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.dto.DemandCondition;
import com.freshcommunity.dto.SubscriptionCreateRequest;
import com.freshcommunity.dto.SubscriptionCreateVO;
import com.freshcommunity.entity.UserSubscription;
import com.freshcommunity.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 导购：需求订阅。
 * - POST   /api/subscriptions        创建订阅（一句话需求，解析并立即匹配）
 * - POST   /api/subscriptions/parse  预览解析结果，不创建订阅
 * - GET    /api/subscriptions        我的订阅列表
 * - DELETE /api/subscriptions/{id}   取消订阅
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/parse")
    public Result<DemandCondition> parse(@Valid @RequestBody SubscriptionCreateRequest req) {
        return Result.success(subscriptionService.preview(req.getText()));
    }

    @PostMapping
    public Result<SubscriptionCreateVO> create(@RequestAttribute("userId") Long userId,
                                               @Valid @RequestBody SubscriptionCreateRequest req) {
        DemandCondition confirmed = req.getKeyword() == null
                ? null
                : new DemandCondition(req.getKeyword(), req.getMaxPrice());
        return Result.success(subscriptionService.create(userId, req.getText(), confirmed));
    }

    @GetMapping
    public Result<List<UserSubscription>> mine(@RequestAttribute("userId") Long userId) {
        return Result.success(subscriptionService.listByUser(userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> cancel(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        subscriptionService.cancel(userId, id);
        return Result.success();
    }
}
