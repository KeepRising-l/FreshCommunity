package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import com.freshcommunity.mapper.GroupBuyMapper;
import com.freshcommunity.mapper.OrderMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final OrderMapper orderMapper;
    private final GroupBuyMapper groupBuyMapper;

    public AdminController(OrderMapper orderMapper, GroupBuyMapper groupBuyMapper) {
        this.orderMapper = orderMapper;
        this.groupBuyMapper = groupBuyMapper;
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Long>> dashboard() {
        Map<String, Long> map = new HashMap<>();
        map.put("todayOrders", orderMapper.countTodayOrders());
        map.put("todaySuccessGroups", groupBuyMapper.countTodaySuccess());
        return Result.success(map);
    }
}
