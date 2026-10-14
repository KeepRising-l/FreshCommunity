package com.freshcommunity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubscriptionCreateRequest {

    @NotBlank(message = "请输入你的需求，例如：10 元以内的苹果")
    @Size(max = 255, message = "需求描述不能超过 255 字")
    private String text;
}
