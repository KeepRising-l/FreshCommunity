package com.freshcommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 导购：用户自然语言需求订阅（取消 = 逻辑删除）。 */
@Data
@TableName("user_subscription")
public class UserSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 归一后的商品关键词 */
    private String keyword;
    /** 价格上限，可为空（用户未提价格时） */
    private BigDecimal maxPrice;
    /** 用户原始输入 */
    private String sourceText;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
