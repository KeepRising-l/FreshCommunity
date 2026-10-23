package com.freshcommunity.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 团长本人团购的经营概览，成交额口径与看板一致。 */
@Data
public class LeaderSummaryVO {
    private Long ongoingGroups;
    private Long pendingPickupOrders;
    private Long completedOrders;
    private BigDecimal totalGmv;
}
