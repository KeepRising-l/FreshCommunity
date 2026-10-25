package com.freshcommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshcommunity.entity.ReviewAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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
     * 批量取多个商品的标签原始行（productId + 逗号分隔 tags），供列表页一次 IN 查询
     * 替代逐商品 N+1；Service 层按 productId 分组后聚合。
     */
    @Select("<script>SELECT a.product_id AS productId, a.tags AS tags FROM review_analysis a "
            + "WHERE a.deleted = 0 AND a.product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> "
            + "AND a.tags IS NOT NULL AND a.tags &lt;&gt; ''</script>")
    List<Map<String, Object>> findTagRowsByProducts(@Param("productIds") List<Long> productIds);

    /**
     * 该商品「产出了口碑标签」的评价数（供提醒摘要"已有 N 人评价都说…"）。
     * 只统计有标签的记录：中性评价不产出标签，若计入会让"都说新鲜"的人数虚高。
     */
    @Select("SELECT COUNT(*) FROM review_analysis a WHERE a.deleted = 0 AND a.product_id = #{productId} "
            + "AND a.tags IS NOT NULL AND a.tags <> ''")
    long countByProduct(Long productId);
}
