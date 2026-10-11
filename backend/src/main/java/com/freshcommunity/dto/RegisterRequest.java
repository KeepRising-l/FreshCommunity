package com.freshcommunity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在 3-20 个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 个字符之间")
    private String password;

    /** 注册角色：USER（默认）或 LEADER；ADMIN 不允许自助注册 */
    private String role;

    /** 团长提货点名称（注册 LEADER 时必填） */
    private String pickupPointName;

    /** 团长提货点地址（注册 LEADER 时可选） */
    private String pickupPointAddress;
}
