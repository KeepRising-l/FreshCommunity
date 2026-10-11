package com.freshcommunity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewVO {
    private Long id;
    private Long orderId;
    private Long productId;
    private Long leaderId;
    private Integer productScore;
    private Integer leaderScore;
    private String content;
    private String username;
    private LocalDateTime createdAt;
}
