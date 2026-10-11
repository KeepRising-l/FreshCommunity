package com.freshcommunity.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团购列表项。相比 GroupBuy 实体额外带上团长与提货点信息，
 * 供用户端"团购列表"页展示（见 group-buying spec：用户浏览团购时能看到对应提货点）。
 */
@Data
public class GroupBuyListVO {
    private Long id;
    private Long leaderId;
    private String title;
    private Integer targetCount;
    private LocalDateTime deadline;
    private String status;
    private Integer memberCount;
    private LocalDateTime createdAt;
    private String leaderName;
    private String pickupPointName;
    private String pickupPointAddress;
}
