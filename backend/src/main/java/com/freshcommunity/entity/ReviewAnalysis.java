package com.freshcommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 导购：评价洞察（每条评价一行，由定时批量任务生成）。 */
@Data
@TableName("review_analysis")
public class ReviewAnalysis {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 1:1 关联评价 */
    private Long reviewId;
    /** 冗余商品 id，供按商品聚合标签 */
    private Long productId;
    /** POSITIVE / NEGATIVE / NEUTRAL */
    private String sentiment;
    /** 逗号分隔关键词 */
    private String tags;
    private LocalDateTime analyzedAt;
    @TableLogic
    private Integer deleted;
}
