package com.freshcommunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshcommunity.common.PageResult;
import com.freshcommunity.dto.OrderVO;
import com.freshcommunity.dto.VerifyRecordVO;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.GroupBuyItem;
import com.freshcommunity.entity.Order;
import com.freshcommunity.entity.Product;
import com.freshcommunity.entity.User;
import com.freshcommunity.mapper.GroupBuyItemMapper;
import com.freshcommunity.mapper.GroupBuyMapper;
import com.freshcommunity.mapper.OrderMapper;
import com.freshcommunity.mapper.ProductMapper;
import com.freshcommunity.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OrderMapper orderMapper;
    private final GroupBuyMapper groupBuyMapper;
    private final GroupBuyItemMapper groupBuyItemMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final PayService payService;
    private final NotificationService notificationService;

    public OrderService(OrderMapper orderMapper, GroupBuyMapper groupBuyMapper,
                        GroupBuyItemMapper groupBuyItemMapper, ProductMapper productMapper,
                        UserMapper userMapper, PayService payService,
                        NotificationService notificationService) {
        this.orderMapper = orderMapper;
        this.groupBuyMapper = groupBuyMapper;
        this.groupBuyItemMapper = groupBuyItemMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.payService = payService;
        this.notificationService = notificationService;
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
                notificationService.notify(o.getUserId(), NotificationService.TYPE_GROUP_SUCCESS,
                        "团购已成团",
                        "你参与的团购已成团，请留意提货通知。取货码：" + o.getPickupCode(),
                        o.getId());
            }
        }
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        String originStatus = order.getStatus();
        if (!"PENDING_PAYMENT".equals(originStatus) && !"PENDING_GROUP".equals(originStatus)) {
            throw new IllegalArgumentException("当前订单不可取消");
        }
        int updated = orderMapper.updateStatus(orderId, "CANCELLED", originStatus);
        if (updated == 0) {
            throw new IllegalStateException("取消失败");
        }
        productMapper.restoreStock(order.getProductId(), order.getQuantity());
        // 待成团订单已计入人数，取消后必须回退，否则会造成"虚假成团"
        if ("PENDING_GROUP".equals(originStatus)) {
            groupBuyMapper.decrementMemberCount(order.getGroupBuyId());
        }
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
        notificationService.notify(order.getUserId(), NotificationService.TYPE_PICKUP_DONE,
                "提货完成", "你的订单已核销完成，感谢参与。", order.getId());
    }

    @Transactional
    public void refundOrderByAdmin(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        String currentStatus = order.getStatus();
        // 状态守卫：终态订单不可退款，避免重复退款反复回补库存
        if ("REFUNDED".equals(currentStatus) || "CANCELLED".equals(currentStatus)
                || "COMPLETED".equals(currentStatus)) {
            throw new IllegalArgumentException("当前订单状态不可退款");
        }
        int updated = orderMapper.updateStatus(orderId, "REFUNDED", currentStatus);
        if (updated == 0) {
            throw new IllegalStateException("退款状态更新失败");
        }
        productMapper.restoreStock(order.getProductId(), order.getQuantity());
        // 待成团订单已计入人数，退款后同步回退
        if ("PENDING_GROUP".equals(currentStatus)) {
            groupBuyMapper.decrementMemberCount(order.getGroupBuyId());
        }
        notificationService.notify(order.getUserId(), NotificationService.TYPE_REFUNDED,
                "订单已退款", "你的订单已被退款，款项将按原路退回。", order.getId());
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

    /**
     * 订单详情：本人可查自己的，管理员可查任意一笔；其余一律按"订单不存在"拒绝，
     * 不区分 404/403，避免越权探测。
     */
    public OrderVO detail(Long orderId, Long userId, String role) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        boolean self = order.getUserId().equals(userId);
        boolean admin = "ADMIN".equals(role);
        if (!self && !admin) {
            throw new IllegalArgumentException("订单不存在");
        }
        return enrich(List.of(order)).get(0);
    }

    /** 管理端订单分页列表（status 等值 + keyword 匹配订单号/买家用户名），join 直出补全字段。 */
    public PageResult<OrderVO> pageForAdmin(String status, String keyword, long page, long size) {
        IPage<OrderVO> result = orderMapper.pageForAdmin(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                blankToNull(status), blankToNull(keyword));
        return PageResult.from(result);
    }

    /**
     * 批量补全订单展示字段（商品/团购/团长/提货点）：三类引用各一次 selectBatchIds，
     * 在 Java 内组 map 回填——列表多大都是常数次查询，不做逐条 N+1。
     * 关联数据被逻辑删除时字段留 null，前端回退占位展示。
     */
    public List<OrderVO> enrich(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> productIds = orders.stream().map(Order::getProductId).distinct().toList();
        List<Long> groupIds = orders.stream().map(Order::getGroupBuyId).distinct().toList();

        Map<Long, Product> products = productIds.isEmpty() ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, GroupBuy> groups = groupIds.isEmpty() ? Collections.emptyMap()
                : groupBuyMapper.selectBatchIds(groupIds).stream()
                        .collect(Collectors.toMap(GroupBuy::getId, Function.identity()));

        List<Long> leaderIds = groups.values().stream().map(GroupBuy::getLeaderId).distinct().toList();
        Map<Long, User> leaders = leaderIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(leaderIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        return orders.stream().map(o -> {
            OrderVO vo = OrderVO.from(o);
            Product p = products.get(o.getProductId());
            if (p != null) {
                vo.setProductName(p.getName());
                vo.setProductImage(p.getImageUrl());
            }
            GroupBuy gb = groups.get(o.getGroupBuyId());
            if (gb != null) {
                vo.setGroupBuyTitle(gb.getTitle());
                User leader = leaders.get(gb.getLeaderId());
                if (leader != null) {
                    vo.setLeaderName(leader.getUsername());
                    vo.setPickupPointName(leader.getPickupPointName());
                    vo.setPickupPointAddress(leader.getPickupPointAddress());
                }
            }
            return vo;
        }).toList();
    }

    private String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public List<VerifyRecordVO> recentVerifiedByLeader(Long leaderId) {
        return orderMapper.findRecentVerifiedByLeader(leaderId);
    }

    private String generateOrderNo() {
        // 毫秒时间戳 + 6 位随机数：同毫秒并发下碰撞概率 1/10^6，
        // 撞上 order_no 唯一索引会落到 DuplicateKey 的 400 分支，量级上可忽略
        return "O" + System.currentTimeMillis() + String.format("%06d", new Random().nextInt(1_000_000));
    }

    private String generatePickupCode() {
        // 生成唯一的 6 位数字取货码，避免碰撞导致 findByPickupCode 抛 TooManyResultsException
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
            if (orderMapper.countByPickupCode(code) == 0) {
                return code;
            }
        }
        throw new IllegalStateException("取货码生成失败，请重试");
    }
}
