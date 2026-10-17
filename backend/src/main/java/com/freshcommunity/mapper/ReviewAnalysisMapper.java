package com.freshcommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshcommunity.entity.ReviewAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewAnalysisMapper extends BaseMapper<ReviewAnalysis> {

    /** 有文字、且尚无分析记录的评价 id（定时批量分析的目标集合）。 */
    @Select("SELECT r.id FROM review r "
            + "LEFT JOIN review_analysis a ON a.review_id = r.id AND a.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.content IS NOT NULL AND TRIM(r.content) <> '' "
            + "AND a.id IS NULL")
    List<Long> findUnanalyzedReviewIds();

    /** 该商品全部洞察标签（逗号分隔字符串，供 Service 层内存聚合）。 */
    @Select("SELECT a.tags FROM review_analysis a "
            + "WHERE a.deleted = 0 AND a.product_id = #{productId} AND a.tags IS NOT NULL AND a.tags <> ''")
    List<String> findTagsByProduct(Long productId);

    /**
     * 该商品「产出了口碑标签」的评价数（供提醒摘要"已有 N 人评价都说…"）。
     * 只统计有标签的记录：中性评价不产出标签，若计入会让"都说新鲜"的人数虚高。
     */
    @Select("SELECT COUNT(*) FROM review_analysis a WHERE a.deleted = 0 AND a.product_id = #{productId} "
            + "AND a.tags IS NOT NULL AND a.tags <> ''")
    long countByProduct(Long productId);
}
