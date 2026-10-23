package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.dto.VerifyRecordVO;
import com.freshcommunity.entity.Order;
import com.freshcommunity.service.OrderService;
import com.freshcommunity.service.ReviewService;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final ReviewService reviewService;

    public OrderController(OrderService orderService, ReviewService reviewService) {
        this.orderService = orderService;
        this.reviewService = reviewService;
    }

    @PostMapping
    public Result<Order> create(@RequestAttribute("userId") Long userId,
                                @RequestParam Long groupBuyId,
                                @RequestParam Integer quantity) {
        return Result.success(orderService.createOrder(userId, groupBuyId, quantity));
    }

    @PostMapping("/{id}/pay")
    public Result<Void> pay(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        orderService.payOrder(userId, id);
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        orderService.cancelOrder(userId, id);
        return Result.success();
    }

    @GetMapping("/mine")
    public Result<List<Order>> mine(@RequestAttribute("userId") Long userId) {
        List<Order> orders = orderService.listByUser(userId);
        Set<Long> reviewedOrderIds = new HashSet<>(reviewService.reviewedOrderIds(userId));
        for (Order o : orders) {
            o.setReviewed(reviewedOrderIds.contains(o.getId()));
        }
        return Result.success(orders);
    }

    @PostMapping("/verify")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('LEADER')")
    public Result<Void> verify(@RequestAttribute("userId") Long leaderId,
                               @RequestParam String pickupCode) {
        orderService.verifyPickupCode(leaderId, pickupCode);
        return Result.success();
    }

    @GetMapping("/verify-history")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('LEADER')")
    public Result<List<VerifyRecordVO>> verifyHistory(@RequestAttribute("userId") Long leaderId) {
        return Result.success(orderService.recentVerifiedByLeader(leaderId));
    }

    @PostMapping("/{id}/refund")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Result<Void> refund(@PathVariable Long id) {
        orderService.refundOrderByAdmin(id);
        return Result.success();
    }
}
