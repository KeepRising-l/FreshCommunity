package com.freshcommunity.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.entity.Order;
import com.freshcommunity.mapper.OrderMapper;
import com.freshcommunity.mapper.ProductMapper;
import com.freshcommunity.service.GroupBuyService;
import com.freshcommunity.service.NotificationService;
import com.freshcommunity.service.ReviewAnalysisService;
import com.freshcommunity.service.SubscriptionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ScheduledJobs {

    private final GroupBuyService groupBuyService;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final NotificationService notificationService;
    private final ReviewAnalysisService reviewAnalysisService;
    private final SubscriptionService subscriptionService;

    public ScheduledJobs(GroupBuyService groupBuyService, OrderMapper orderMapper,
                         ProductMapper productMapper, NotificationService notificationService,
                         ReviewAnalysisService reviewAnalysisService,
                         SubscriptionService subscriptionService) {
        this.groupBuyService = groupBuyService;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.notificationService = notificationService;
        this.reviewAnalysisService = reviewAnalysisService;
        this.subscriptionService = subscriptionService;
    }

    /**
     * 首次执行延迟（毫秒）。生产默认 0（启动即跑）；测试 profile 设为大值，
     * 避免定时任务与测试库重建 schema（DROP → CREATE 之间）抢跑而刷出假错误。
     */
    @Scheduled(fixedDelay = 60000, initialDelayString = "${scheduling.initial-delay-ms:0}")
    @Transactional
    public void processExpiredGroupBuys() {
        groupBuyService.handleExpiredGroups();
    }

    @Scheduled(fixedDelay = 60000, initialDelayString = "${scheduling.initial-delay-ms:0}")
    @Transactional
    public void closePendingPaymentOrders() {
        List<Order> expired = orderMapper.findPendingPaymentExpired();
        for (Order o : expired) {
            int updated = orderMapper.updateStatus(o.getId(), "CANCELLED", "PENDING_PAYMENT");
            if (updated > 0) {
                productMapper.restoreStock(o.getProductId(), o.getQuantity());
                notificationService.notify(o.getUserId(), NotificationService.TYPE_ORDER_TIMEOUT,
                        "订单已关闭", "你的订单超过 30 分钟未支付，已自动关闭。", o.getId());
            }
        }
    }

    @Scheduled(fixedDelay = 60000, initialDelayString = "${scheduling.initial-delay-ms:0}")
    @Transactional
    public void analyzePendingReviews() {
        reviewAnalysisService.analyzePending();
    }

    /**
     * 订阅匹配补偿：关闭"建订阅 × 开团"并发互相错过导致的漏提醒窗口。
     * 幂等——重复扫描对已发送的组合只是 INSERT IGNORE 空转，不会重复提醒。
     */
    @Scheduled(fixedDelay = 60000, initialDelayString = "${scheduling.initial-delay-ms:0}")
    @Transactional
    public void compensateSubscriptionMatches() {
        subscriptionService.compensateMissed();
    }
}
