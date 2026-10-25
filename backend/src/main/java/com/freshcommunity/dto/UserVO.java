package com.freshcommunity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String role;
    private String pickupPointName;
    private String pickupPointAddress;
    /** 注册时间（个人中心展示），纯增量字段 */
    private LocalDateTime createdAt;
}
