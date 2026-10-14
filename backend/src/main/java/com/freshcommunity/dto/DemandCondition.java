package com.freshcommunity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** 需求解析结果：商品关键词 + 可选价格上限（用户未提价格时为 null）。 */
@Data
@AllArgsConstructor
public class DemandCondition {
    private String keyword;
    private BigDecimal maxPrice;
}
