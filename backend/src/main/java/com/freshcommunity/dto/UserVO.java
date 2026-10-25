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
    /** 头像（全角色）与提货点照片（团长），来自 user 表 */
    private String avatarUrl;
    private String pickupPointImageUrl;
    /** 注册时间（个人中心展示），纯增量字段 */
    private LocalDateTime createdAt;
}
