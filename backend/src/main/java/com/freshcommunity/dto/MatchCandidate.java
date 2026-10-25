package com.freshcommunity.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 补偿扫描的候选行：一条「订阅 × 进行中团购」待匹配组合，
 * 附带发提醒所需的团购进度与商品信息（与内存匹配路径所需字段一致）。
 */
@Data
public class MatchCandidate {
    private Long subscriptionId;
    private Long userId;
    private Long groupBuyId;
    private Integer targetCount;
    private Integer memberCount;
    private BigDecimal priceSnapshot;
    private Long productId;
    private String productName;
}
