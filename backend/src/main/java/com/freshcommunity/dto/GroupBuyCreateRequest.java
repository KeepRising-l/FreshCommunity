package com.freshcommunity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

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
}
