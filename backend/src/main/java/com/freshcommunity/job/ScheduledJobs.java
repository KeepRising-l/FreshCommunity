package com.freshcommunity.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.entity.Order;
import com.freshcommunity.mapper.OrderMapper;
import com.freshcommunity.mapper.ProductMapper;
import com.freshcommunity.service.GroupBuyService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ScheduledJobs {

    private final GroupBuyService groupBuyService;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;

    public ScheduledJobs(GroupBuyService groupBuyService, OrderMapper orderMapper, ProductMapper productMapper) {
        this.groupBuyService = groupBuyService;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processExpiredGroupBuys() {
        groupBuyService.handleExpiredGroups();
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void closePendingPaymentOrders() {
        List<Order> expired = orderMapper.findPendingPaymentExpired();
        for (Order o : expired) {
            int updated = orderMapper.updateStatus(o.getId(), "CANCELLED", "PENDING_PAYMENT");
            if (updated > 0) {
                productMapper.restoreStock(o.getProductId(), o.getQuantity());
            }
        }
    }
}
