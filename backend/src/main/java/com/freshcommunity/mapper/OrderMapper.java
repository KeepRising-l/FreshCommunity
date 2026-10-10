package com.freshcommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshcommunity.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Select("SELECT COUNT(*) FROM `order` WHERE DATE(created_at) = CURDATE()")
    Long countTodayOrders();
}
