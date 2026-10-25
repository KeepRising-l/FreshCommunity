package com.freshcommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshcommunity.dto.MatchCandidate;
import com.freshcommunity.entity.SubscriptionMatch;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SubscriptionMatchMapper extends BaseMapper<SubscriptionMatch> {

    /**
     * 原子抢占匹配记录：唯一键冲突时忽略，返回 1 表示本次首占（应发提醒），
     * 返回 0 表示已有记录（已被其他路径/此前发送过）。一条语句完成判定与占位。
     */
    @Insert("INSERT IGNORE INTO subscription_match (subscription_id, group_buy_id) " +
            "VALUES (#{subscriptionId}, #{groupBuyId})")
    int tryInsert(@Param("subscriptionId") Long subscriptionId, @Param("groupBuyId") Long groupBuyId);

    /**
     * 补偿扫描：找出"符合条件但尚无匹配记录"的「订阅 × 进行中团购」组合。
     * 匹配条件与内存版 matches() 一致——商品名包含订阅关键词（子串），
     * 且拼团价不超过订阅价格上限（为空则不限价）。软删表全部显式带 deleted=0。
     */
    @Select("SELECT us.id AS subscriptionId, us.user_id AS userId, " +
            "gb.id AS groupBuyId, gb.target_count AS targetCount, gb.member_count AS memberCount, " +
            "gi.price_snapshot AS priceSnapshot, p.id AS productId, p.name AS productName " +
            "FROM user_subscription us " +
            "JOIN group_buy gb ON gb.status = 'ONGOING' AND gb.deleted = 0 AND gb.deadline > NOW() " +
            "JOIN group_buy_item gi ON gi.group_buy_id = gb.id AND gi.deleted = 0 " +
            "JOIN product p ON p.id = gi.product_id AND p.deleted = 0 " +
            "LEFT JOIN subscription_match sm " +
            "  ON sm.subscription_id = us.id AND sm.group_buy_id = gb.id AND sm.deleted = 0 " +
            "WHERE us.deleted = 0 " +
            "AND p.name LIKE CONCAT('%', us.keyword, '%') " +
            "AND (us.max_price IS NULL OR gi.price_snapshot <= us.max_price) " +
            "AND sm.id IS NULL")
    List<MatchCandidate> findMissedMatches();
}
