package com.freshcommunity.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 团长视角的评价列表项：本人名下被评记录，附商品名与订单号便于定位处理。 */
@Data
public class LeaderReviewVO {
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer productScore;
    private Integer leaderScore;
    private String content;
    private String leaderReply;
    private LocalDateTime leaderRepliedAt;
    private String username;
    private String productName;
    private String orderNo;
    private LocalDateTime createdAt;
}
