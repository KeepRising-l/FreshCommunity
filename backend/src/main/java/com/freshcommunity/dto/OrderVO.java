package com.freshcommunity.dto;

import com.freshcommunity.entity.Order;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单展示 VO：在 Order 实体之上补全商品 / 团购 / 团长 / 提货点等只读上下文，
 * 前端"我的订单"、订单详情、团购订单列表与管理端订单列表共用。
 * 补全方式二选一：管理端走 SQL join 直出，其余走 OrderService.enrich 批量回填。
 * 关联数据被逻辑删除时对应字段为 null，前端回退占位展示，接口不报错。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderVO extends Order {
    private String productName;
    private String productImage;
    private String groupBuyTitle;
    /** 团长用户名 */
    private String leaderName;
    private String pickupPointName;
    private String pickupPointAddress;
    /** 买家用户名（仅管理端订单列表返回） */
    private String buyerName;

    public static OrderVO from(Order order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setGroupBuyId(order.getGroupBuyId());
        vo.setProductId(order.getProductId());
        vo.setQuantity(order.getQuantity());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setPickupCode(order.getPickupCode());
        vo.setPayTime(order.getPayTime());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setUpdatedAt(order.getUpdatedAt());
        vo.setReviewed(order.getReviewed());
        return vo;
    }
}
