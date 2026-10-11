package com.freshcommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshcommunity.dto.ReviewVO;
import com.freshcommunity.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    @Select("SELECT COUNT(*) FROM review WHERE product_id = #{productId} AND deleted = 0")
    long countByProduct(@Param("productId") Long productId);

    @Select("SELECT AVG(product_score) FROM review WHERE product_id = #{productId} AND deleted = 0")
    Double avgProductScore(@Param("productId") Long productId);

    @Select("SELECT COUNT(*) FROM review WHERE leader_id = #{leaderId} AND deleted = 0")
    long countByLeader(@Param("leaderId") Long leaderId);

    @Select("SELECT AVG(leader_score) FROM review WHERE leader_id = #{leaderId} AND deleted = 0")
    Double avgLeaderScore(@Param("leaderId") Long leaderId);

    @Select("SELECT r.*, u.username AS username FROM review r "
            + "LEFT JOIN user u ON u.id = r.user_id "
            + "WHERE r.product_id = #{productId} AND r.deleted = 0 "
            + "ORDER BY r.created_at DESC")
    List<ReviewVO> listVOByProduct(@Param("productId") Long productId);

    @Select("SELECT order_id FROM review WHERE user_id = #{userId} AND deleted = 0")
    List<Long> findOrderIdsByUser(@Param("userId") Long userId);
}
