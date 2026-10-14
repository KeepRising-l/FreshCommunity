package com.freshcommunity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订阅创建结果：解析回显 + 立即命中的进行中团购数量。 */
@Data
public class SubscriptionCreateVO {
    private Long id;
    /** 归一后的商品关键词 */
    private String keyword;
    /** 价格上限，可为空 */
    private BigDecimal maxPrice;
    /** 用户原始输入 */
    private String sourceText;
    private LocalDateTime createdAt;
    /** 订阅时立即匹配到的进行中团购数量（每条已同时发送站内信） */
    private Integer matchedCount;
}
