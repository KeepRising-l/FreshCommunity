package com.freshcommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshcommunity.dto.AdminDashboardVO;
import com.freshcommunity.dto.VerifyRecordVO;
import com.freshcommunity.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Update("UPDATE `order` SET status = #{status} WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("expectedStatus") String expectedStatus);

    @Select("SELECT * FROM `order` WHERE group_buy_id = #{groupBuyId} AND status = #{status}")
    List<Order> findByGroupBuyAndStatus(@Param("groupBuyId") Long groupBuyId, @Param("status") String status);

    @Select("SELECT * FROM `order` WHERE status = 'PENDING_PAYMENT' AND created_at <= DATE_SUB(NOW(), INTERVAL 30 MINUTE)")
    List<Order> findPendingPaymentExpired();

    @Select("SELECT * FROM `order` WHERE pickup_code = #{code}")
    Order findByPickupCode(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM `order` WHERE pickup_code = #{code}")
    long countByPickupCode(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM `order` WHERE DATE(created_at) = CURDATE() AND deleted = 0")
    Long countTodayOrders();

    // ---------- 看板与团长概览（手写 SQL 不会自动追加逻辑删除条件，必须显式带 deleted = 0） ----------

    @Select("SELECT COALESCE(SUM(amount), 0) FROM `order` " +
            "WHERE DATE(created_at) = CURDATE() AND deleted = 0 " +
            "AND status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'REFUNDED')")
    BigDecimal sumTodayGmv();

    @Select("SELECT status, COUNT(*) AS `count` FROM `order` WHERE deleted = 0 GROUP BY status")
    List<AdminDashboardVO.StatusCount> countGroupByStatus();

    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, COUNT(*) AS orders, " +
            "COALESCE(SUM(CASE WHEN status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'REFUNDED') " +
            "THEN amount ELSE 0 END), 0) AS gmv " +
            "FROM `order` WHERE deleted = 0 AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')")
    List<AdminDashboardVO.TrendPoint> trendLast7Days();

    @Select("SELECT COUNT(*) FROM `order` o JOIN group_buy gb ON o.group_buy_id = gb.id " +
            "WHERE gb.leader_id = #{leaderId} AND o.status = #{status} " +
            "AND o.deleted = 0 AND gb.deleted = 0")
    Long countByLeaderAndStatus(@Param("leaderId") Long leaderId, @Param("status") String status);

    // 成团后订单落 SUCCESS（已发取货码、待上门）；PENDING_PICKUP 为历史状态，一并计入待取货
    @Select("SELECT COUNT(*) FROM `order` o JOIN group_buy gb ON o.group_buy_id = gb.id " +
            "WHERE gb.leader_id = #{leaderId} AND o.status IN ('SUCCESS', 'PENDING_PICKUP') " +
            "AND o.deleted = 0 AND gb.deleted = 0")
    Long countPendingPickupByLeader(@Param("leaderId") Long leaderId);

    @Select("SELECT COALESCE(SUM(o.amount), 0) FROM `order` o JOIN group_buy gb ON o.group_buy_id = gb.id " +
            "WHERE gb.leader_id = #{leaderId} AND o.deleted = 0 AND gb.deleted = 0 " +
            "AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'REFUNDED')")
    BigDecimal sumGmvByLeader(@Param("leaderId") Long leaderId);

    @Select("SELECT o.order_no AS orderNo, gb.title AS groupBuyTitle, o.quantity AS quantity, " +
            "o.amount AS amount, o.updated_at AS verifiedAt " +
            "FROM `order` o JOIN group_buy gb ON o.group_buy_id = gb.id " +
            "WHERE gb.leader_id = #{leaderId} AND o.status = 'COMPLETED' " +
            "AND o.deleted = 0 AND gb.deleted = 0 " +
            "ORDER BY o.updated_at DESC LIMIT 10")
    List<VerifyRecordVO> findRecentVerifiedByLeader(@Param("leaderId") Long leaderId);
}
