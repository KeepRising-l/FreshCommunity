package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.entity.Order;
import com.freshcommunity.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
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
        return Result.success(orderService.listByUser(userId));
    }

    @PostMapping("/verify")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('LEADER')")
    public Result<Void> verify(@RequestAttribute("userId") Long leaderId,
                               @RequestParam String pickupCode) {
        orderService.verifyPickupCode(leaderId, pickupCode);
        return Result.success();
    }

    @PostMapping("/{id}/refund")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Result<Void> refund(@PathVariable Long id) {
        orderService.refundOrderByAdmin(id);
        return Result.success();
    }
}
