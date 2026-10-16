package com.freshcommunity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubscriptionCreateRequest {

    @NotBlank(message = "请输入你的需求，例如：10 元以内的苹果")
    @Size(max = 255, message = "需求描述不能超过 255 字")
    private String text;

    /** 可选：用户确认的预览条件；未提供时由服务端按 text 重新解析。 */
    @Size(max = 64, message = "商品关键词不能超过 64 字")
    private String keyword;

    @DecimalMin(value = "0.00", message = "价格上限不能小于 0")
    private BigDecimal maxPrice;
}
