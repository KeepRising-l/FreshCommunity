package com.freshcommunity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 团购列表项。相比 GroupBuy 实体额外带上团长、提货点与所挂商品信息，
 * 供用户端"团购列表"页展示（见 group-buying spec：用户浏览团购时能看到对应提货点）。
 *
 * <p>商品与价格来自 group_buy_item（拼团价快照）与 product（原价、图片），
 * 团购本身不存价格，所以必须在这里透出，否则前端列表无法展示拼团价。
 */
@Data
public class GroupBuyListVO {
    private Long id;
    private Long leaderId;
    private String title;
    private Integer targetCount;
    private LocalDateTime deadline;
    private String status;
    private Integer memberCount;
    private LocalDateTime createdAt;
    private String leaderName;
    private String pickupPointName;
    private String pickupPointAddress;

    /** 所挂商品 */
    private Long productId;
    private String productName;
    private String imageUrl;
    /** 拼团价（group_buy_item.price_snapshot） */
    private BigDecimal price;
    /** 商品原价（product.price） */
    private BigDecimal originalPrice;
    /** 商品口碑标签（来自评价洞察，无数据时为空列表） */
    private java.util.List<String> insightTags;
}
