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
    /** 团长回复（一层，未回复为 null） */
    private String leaderReply;
    private LocalDateTime leaderRepliedAt;
    private String username;
    private LocalDateTime createdAt;
}
