package com.freshcommunity.dto;

import lombok.Data;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String role;
    private String pickupPointName;
    private String pickupPointAddress;
}
