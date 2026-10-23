package com.freshcommunity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理看板数据。todayOrders / todaySuccessGroups 沿用旧字段名，其余为本次扩展。
 */
@Data
public class AdminDashboardVO {

    private Long todayOrders;
    private Long todaySuccessGroups;
    private BigDecimal todayGmv;
    private Long totalUsers;
    private Long ongoingGroupBuys;
    private List<StatusCount> orderStatusDistribution;
    private List<TrendPoint> trend7d;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCount {
        private String status;
        private Long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        /** yyyy-MM-dd */
        private String date;
        private Long orders;
        private BigDecimal gmv;
    }
}
