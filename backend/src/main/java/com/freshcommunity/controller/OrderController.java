package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.dto.OrderVO;
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

    /** 本人订单列表：VO 自带商品/团购/团长/提货点展示字段，前端无需再拉商品列表做映射。 */
    @GetMapping("/mine")
    public Result<List<OrderVO>> mine(@RequestAttribute("userId") Long userId) {
        List<Order> orders = orderService.listByUser(userId);
        Set<Long> reviewedOrderIds = new HashSet<>(reviewService.reviewedOrderIds(userId));
        List<OrderVO> vos = orderService.enrich(orders);
        for (OrderVO vo : vos) {
            vo.setReviewed(reviewedOrderIds.contains(vo.getId()));
        }
        return Result.success(vos);
    }

    /** 订单详情：本人或管理员可查（订单详情页数据源）。 */
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@RequestAttribute("userId") Long userId,
                                  @RequestAttribute("role") String role,
                                  @PathVariable Long id) {
        return Result.success(orderService.detail(id, userId, role));
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
