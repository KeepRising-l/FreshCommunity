package com.freshcommunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.GroupBuyItem;
import com.freshcommunity.entity.Order;
import com.freshcommunity.entity.Product;
import com.freshcommunity.mapper.GroupBuyItemMapper;
import com.freshcommunity.mapper.GroupBuyMapper;
import com.freshcommunity.mapper.OrderMapper;
import com.freshcommunity.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final GroupBuyMapper groupBuyMapper;
    private final GroupBuyItemMapper groupBuyItemMapper;
    private final ProductMapper productMapper;
    private final PayService payService;

    public OrderService(OrderMapper orderMapper, GroupBuyMapper groupBuyMapper,
                        GroupBuyItemMapper groupBuyItemMapper, ProductMapper productMapper,
                        PayService payService) {
        this.orderMapper = orderMapper;
        this.groupBuyMapper = groupBuyMapper;
        this.groupBuyItemMapper = groupBuyItemMapper;
        this.productMapper = productMapper;
        this.payService = payService;
    }

    @Transactional
    public Order createOrder(Long userId, Long groupBuyId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("购买数量必须大于0");
        }
        GroupBuy groupBuy = groupBuyMapper.selectById(groupBuyId);
        if (groupBuy == null || !"ONGOING".equals(groupBuy.getStatus())) {
            throw new IllegalArgumentException("团购不在进行中");
        }
        if (groupBuy.getDeadline().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("团购已截止");
        }
        List<GroupBuyItem> items = groupBuyItemMapper.selectList(
                new LambdaQueryWrapper<GroupBuyItem>().eq(GroupBuyItem::getGroupBuyId, groupBuyId));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("团购商品不存在");
        }
        GroupBuyItem item = items.get(0);

        int affected = productMapper.deductStock(item.getProductId(), quantity);
        if (affected == 0) {
            throw new IllegalArgumentException("库存不足");
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setGroupBuyId(groupBuyId);
        order.setProductId(item.getProductId());
        order.setQuantity(quantity);
        order.setAmount(item.getPriceSnapshot().multiply(BigDecimal.valueOf(quantity)));
        order.setStatus("PENDING_PAYMENT");
        orderMapper.insert(order);
        return order;
    }

    @Transactional
    public void payOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new IllegalArgumentException("订单状态错误");
        }
        if (!payService.pay(order)) {
            throw new IllegalStateException("支付失败");
        }
        int updated = orderMapper.updateStatus(orderId, "PENDING_GROUP", "PENDING_PAYMENT");
        if (updated == 0) {
            throw new IllegalStateException("支付状态更新失败");
        }
        order.setStatus("PENDING_GROUP");
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);

        groupBuyMapper.incrementMemberCount(order.getGroupBuyId());
        tryGroupSuccess(order.getGroupBuyId());
    }

    @Transactional
    public void tryGroupSuccess(Long groupBuyId) {
        int success = groupBuyMapper.trySuccess(groupBuyId);
        if (success > 0) {
            List<Order> orders = orderMapper.findByGroupBuyAndStatus(groupBuyId, "PENDING_GROUP");
            for (Order o : orders) {
                o.setStatus("SUCCESS");
                o.setPickupCode(generatePickupCode());
                orderMapper.updateById(o);
            }
        }
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus()) && !"PENDING_GROUP".equals(order.getStatus())) {
            throw new IllegalArgumentException("当前订单不可取消");
        }
        int updated = orderMapper.updateStatus(orderId, "CANCELLED", order.getStatus());
        if (updated == 0) {
            throw new IllegalStateException("取消失败");
        }
        productMapper.restoreStock(order.getProductId(), order.getQuantity());
    }

    @Transactional
    public void verifyPickupCode(Long leaderId, String code) {
        Order order = orderMapper.findByPickupCode(code);
        if (order == null) {
            throw new IllegalArgumentException("取货码无效");
        }
        GroupBuy groupBuy = groupBuyMapper.selectById(order.getGroupBuyId());
        if (groupBuy == null || !groupBuy.getLeaderId().equals(leaderId)) {
            throw new IllegalArgumentException("无权核销该订单");
        }
        if (!"SUCCESS".equals(order.getStatus()) && !"PENDING_PICKUP".equals(order.getStatus())) {
            throw new IllegalArgumentException("订单状态不允许核销");
        }
        orderMapper.updateStatus(order.getId(), "COMPLETED", order.getStatus());
    }

    @Transactional
    public void refundOrderByAdmin(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        String next = "REFUNDED";
        int updated = orderMapper.updateStatus(orderId, next, order.getStatus());
        if (updated == 0) {
            throw new IllegalStateException("退款状态更新失败");
        }
        productMapper.restoreStock(order.getProductId(), order.getQuantity());
    }

    public List<Order> getPendingGroupOrders(Long groupBuyId) {
        return orderMapper.findByGroupBuyAndStatus(groupBuyId, "PENDING_GROUP");
    }

    public void updateOrderStatus(Order order) {
        orderMapper.updateById(order);
    }

    public List<Order> listByUser(Long userId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<Order>().eq(Order::getUserId, userId).orderByDesc(Order::getCreatedAt));
    }

    public List<Order> listByGroupBuy(Long groupBuyId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<Order>().eq(Order::getGroupBuyId, groupBuyId).orderByDesc(Order::getCreatedAt));
    }

    private String generateOrderNo() {
        return "O" + System.currentTimeMillis() + new Random().nextInt(1000);
    }

    private String generatePickupCode() {
        Random rand = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(rand.nextInt(10));
        }
        return sb.toString();
    }
}
