package com.freshcommunity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GroupBuyCreateRequest {
    @NotBlank(message = "团购标题不能为空")
    private String title;
    @NotNull(message = "商品ID不能为空")
    private Long productId;
    @NotNull(message = "成团人数不能为空")
    @Min(value = 1, message = "成团人数至少1人")
    private Integer targetCount;
    @NotNull(message = "截止时间不能为空")
    private LocalDateTime deadline;
    /**
     * 拼团价（可选）。不传时取商品零售价快照；传了则必须大于 0 且不高于零售价，
     * 否则「立省」为负，前端展示会失真。
     */
    private BigDecimal price;
}
