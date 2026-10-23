package com.freshcommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshcommunity.entity.GroupBuy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GroupBuyMapper extends BaseMapper<GroupBuy> {

    @Update("UPDATE group_buy SET member_count = member_count + 1 WHERE id = #{id} AND status = 'ONGOING'")
    int incrementMemberCount(@Param("id") Long id);

    @Update("UPDATE group_buy SET member_count = member_count - 1 WHERE id = #{id} AND member_count > 0")
    int decrementMemberCount(@Param("id") Long id);

    @Update("UPDATE group_buy SET status = 'SUCCESS' WHERE id = #{id} AND status = 'ONGOING' AND member_count >= target_count")
    int trySuccess(@Param("id") Long id);

    @Update("UPDATE group_buy SET status = 'FAIL' WHERE id = #{id} AND status = 'ONGOING' AND deadline <= NOW() AND member_count < target_count")
    int tryFail(@Param("id") Long id);

    @Select("SELECT * FROM group_buy WHERE status = 'ONGOING' AND deadline <= NOW()")
    java.util.List<GroupBuy> findExpiredOngoing();

    @Select("SELECT COUNT(*) FROM group_buy WHERE status = 'SUCCESS' AND DATE(updated_at) = CURDATE() AND deleted = 0")
    Long countTodaySuccess();

    @Select("SELECT COUNT(*) FROM group_buy WHERE status = 'ONGOING' AND deleted = 0")
    Long countOngoing();

    @Select("SELECT COUNT(*) FROM group_buy WHERE leader_id = #{leaderId} AND status = 'ONGOING' AND deleted = 0")
    Long countOngoingByLeader(@Param("leaderId") Long leaderId);
}
