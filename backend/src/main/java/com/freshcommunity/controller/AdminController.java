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

    /**
     * 订单 CSV 导出：与订单管理页同口径（status/keyword 筛选），上限 5000 行。
     * 带 UTF-8 BOM，Excel 直接打开中文不乱码。
     */
    @GetMapping(value = "/orders/export", produces = "text/csv;charset=UTF-8")
    public org.springframework.http.ResponseEntity<String> exportOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        PageResult<OrderVO> page = orderService.pageForAdmin(status, keyword, 1, 5000);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM
        sb.append("订单号,买家,商品,团购,数量,金额,状态,下单时间,支付时间,取货码\n");
        for (OrderVO o : page.getRecords()) {
            sb.append(csv(o.getOrderNo())).append(',')
              .append(csv(o.getBuyerName())).append(',')
              .append(csv(o.getProductName())).append(',')
              .append(csv(o.getGroupBuyTitle())).append(',')
              .append(o.getQuantity()).append(',')
              .append(o.getAmount() == null ? "" : o.getAmount().toPlainString()).append(',')
              .append(csv(o.getStatus())).append(',')
              .append(csv(o.getCreatedAt() == null ? null : o.getCreatedAt().toString())).append(',')
              .append(csv(o.getPayTime() == null ? null : o.getPayTime().toString())).append(',')
              .append(csv(o.getPickupCode() == null ? "" : o.getPickupCode()))
              .append('\n');
        }
        String filename = java.net.URLEncoder.encode("订单导出_" + java.time.LocalDate.now() + ".csv",
                java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + filename)
                .body(sb.toString());
    }

    /** CSV 字段转义：包含逗号/引号/换行时整体加引号并双写引号。 */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
