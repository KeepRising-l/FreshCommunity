package com.freshcommunity.controller;

import com.freshcommunity.common.PageResult;
import com.freshcommunity.common.Result;
import com.freshcommunity.dto.AdminDashboardVO;
import com.freshcommunity.dto.OrderVO;
import com.freshcommunity.service.AdminDashboardService;
import com.freshcommunity.service.OrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminDashboardService adminDashboardService;
    private final OrderService orderService;

    public AdminController(AdminDashboardService adminDashboardService, OrderService orderService) {
        this.adminDashboardService = adminDashboardService;
        this.orderService = orderService;
    }

    @GetMapping("/dashboard")
    public Result<AdminDashboardVO> dashboard() {
        return Result.success(adminDashboardService.dashboard());
    }

    /**
     * 管理端订单列表：status 等值筛选，keyword 同时匹配订单号与买家用户名（均模糊），
     * 创建时间倒序分页。订单管理页数据源（退款兜底的前端入口）。
     */
    @GetMapping("/orders")
    public Result<PageResult<OrderVO>> orders(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false, defaultValue = "1") long page,
                                              @RequestParam(required = false, defaultValue = "10") long size) {
        return Result.success(orderService.pageForAdmin(status, keyword, page, size));
    }
}
