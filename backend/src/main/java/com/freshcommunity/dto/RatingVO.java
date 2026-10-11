package com.freshcommunity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 评分汇总。无评价时 avgScore 为 null、count 为 0，
 * 避免用 0 分误导用户（见 reviews spec 的"无评价时的汇总"场景）。
 */
@Data
@AllArgsConstructor
public class RatingVO {
    private Double avgScore;
    private Long count;
}
