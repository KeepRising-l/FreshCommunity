package com.freshcommunity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewCreateRequest {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "商品评分不能为空")
    @Min(value = 1, message = "商品评分需在 1-5 之间")
    @Max(value = 5, message = "商品评分需在 1-5 之间")
    private Integer productScore;

    @NotNull(message = "团长评分不能为空")
    @Min(value = 1, message = "团长评分需在 1-5 之间")
    @Max(value = 5, message = "团长评分需在 1-5 之间")
    private Integer leaderScore;

    @Size(max = 500, message = "评论不能超过 500 字")
    private String content;
}
