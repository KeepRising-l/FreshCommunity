package com.freshcommunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.dto.GroupBuyCreateRequest;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.GroupBuyItem;
import com.freshcommunity.entity.Product;
import com.freshcommunity.mapper.GroupBuyItemMapper;
import com.freshcommunity.mapper.GroupBuyMapper;
import com.freshcommunity.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GroupBuyService {

    private final GroupBuyMapper groupBuyMapper;
    private final GroupBuyItemMapper groupBuyItemMapper;
    private final ProductMapper productMapper;
    private final OrderService orderService;
    private final NotificationService notificationService;

    public GroupBuyService(GroupBuyMapper groupBuyMapper, GroupBuyItemMapper groupBuyItemMapper,
                           ProductMapper productMapper, OrderService orderService,
                           NotificationService notificationService) {
        this.groupBuyMapper = groupBuyMapper;
        this.groupBuyItemMapper = groupBuyItemMapper;
        this.productMapper = productMapper;
        this.orderService = orderService;
        this.notificationService = notificationService;
    }

    @Transactional
    public GroupBuy createGroupBuy(Long leaderId, GroupBuyCreateRequest req) {
        if (req.getDeadline() == null || req.getDeadline().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("截止时间必须晚于当前时间");
        }
        if (req.getTargetCount() == null || req.getTargetCount() <= 0) {
            throw new IllegalArgumentException("成团人数必须大于0");
        }
        Product product = productMapper.selectById(req.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("商品不存在");
        }

        GroupBuy groupBuy = new GroupBuy();
        groupBuy.setLeaderId(leaderId);
        groupBuy.setTitle(req.getTitle());
        groupBuy.setTargetCount(req.getTargetCount());
        groupBuy.setDeadline(req.getDeadline());
        groupBuy.setStatus("ONGOING");
        groupBuy.setMemberCount(0);
        groupBuyMapper.insert(groupBuy);

        GroupBuyItem item = new GroupBuyItem();
        item.setGroupBuyId(groupBuy.getId());
        item.setProductId(product.getId());
        item.setPriceSnapshot(product.getPrice());
        groupBuyItemMapper.insert(item);

        return groupBuy;
    }

    public List<GroupBuy> listOngoing() {
        return groupBuyMapper.selectList(
                new LambdaQueryWrapper<GroupBuy>()
                        .eq(GroupBuy::getStatus, "ONGOING")
                        .gt(GroupBuy::getDeadline, LocalDateTime.now())
                        .orderByDesc(GroupBuy::getCreatedAt));
    }

    public List<GroupBuy> listByLeader(Long leaderId) {
        return groupBuyMapper.selectList(
                new LambdaQueryWrapper<GroupBuy>()
                        .eq(GroupBuy::getLeaderId, leaderId)
                        .orderByDesc(GroupBuy::getCreatedAt));
    }

    public GroupBuy detail(Long id) {
        return groupBuyMapper.selectById(id);
    }

    @Transactional
    public void handleExpiredGroups() {
        List<GroupBuy> expired = groupBuyMapper.findExpiredOngoing();
        for (GroupBuy gb : expired) {
            int fail = groupBuyMapper.tryFail(gb.getId());
            if (fail > 0) {
                List<com.freshcommunity.entity.Order> orders = orderService.getPendingGroupOrders(gb.getId());
                for (com.freshcommunity.entity.Order o : orders) {
                    o.setStatus("REFUNDED");
                    orderService.updateOrderStatus(o);
                    productMapper.restoreStock(o.getProductId(), o.getQuantity());
                    notificationService.notify(o.getUserId(), NotificationService.TYPE_GROUP_FAILED,
                            "团购未成团",
                            "你参与的团购到点未成团，已自动解散并全额退款。",
                            o.getId());
                }
            } else {
                orderService.tryGroupSuccess(gb.getId());
            }
        }
    }
}
