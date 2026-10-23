package com.freshcommunity.service;

import com.freshcommunity.dto.AdminDashboardVO;
import com.freshcommunity.mapper.GroupBuyMapper;
import com.freshcommunity.mapper.OrderMapper;
import com.freshcommunity.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminDashboardService {

    private static final int TREND_DAYS = 7;

    private final OrderMapper orderMapper;
    private final GroupBuyMapper groupBuyMapper;
    private final UserMapper userMapper;

    public AdminDashboardService(OrderMapper orderMapper, GroupBuyMapper groupBuyMapper,
                                 UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.groupBuyMapper = groupBuyMapper;
        this.userMapper = userMapper;
    }

    public AdminDashboardVO dashboard() {
        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setTodayOrders(orderMapper.countTodayOrders());
        vo.setTodaySuccessGroups(groupBuyMapper.countTodaySuccess());
        vo.setTodayGmv(orderMapper.sumTodayGmv());
        vo.setTotalUsers(userMapper.countAll());
        vo.setOngoingGroupBuys(groupBuyMapper.countOngoing());
        vo.setOrderStatusDistribution(orderMapper.countGroupByStatus());
        vo.setTrend7d(fillMissingDays(orderMapper.trendLast7Days()));
        return vo;
    }

    /** SQL 只返回有订单的日期，这里补成连续 7 个点，缺失日计 0。 */
    private List<AdminDashboardVO.TrendPoint> fillMissingDays(List<AdminDashboardVO.TrendPoint> rows) {
        Map<String, AdminDashboardVO.TrendPoint> byDate = new HashMap<>();
        for (AdminDashboardVO.TrendPoint row : rows) {
            byDate.put(row.getDate(), row);
        }
        List<AdminDashboardVO.TrendPoint> trend = new ArrayList<>(TREND_DAYS);
        LocalDate today = LocalDate.now();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            trend.add(byDate.getOrDefault(date,
                    new AdminDashboardVO.TrendPoint(date, 0L, BigDecimal.ZERO)));
        }
        return trend;
    }
}
