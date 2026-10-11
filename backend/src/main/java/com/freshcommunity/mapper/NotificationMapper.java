package com.freshcommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshcommunity.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND is_read = 0 AND deleted = 0")
    long countUnread(@Param("userId") Long userId);

    @Update("UPDATE notification SET is_read = 1 WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE notification SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0 AND deleted = 0")
    int markAllRead(@Param("userId") Long userId);
}
