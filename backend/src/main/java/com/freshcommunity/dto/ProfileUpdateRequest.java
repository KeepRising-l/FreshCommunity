package com.freshcommunity.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料维护。当前仅团长有可维护字段（提货点），
 * 普通用户与管理员提交会被拒绝（见 user-roles spec「团长维护提货点」）。
 */
@Data
public class ProfileUpdateRequest {

    @Size(max = 128, message = "提货点名称不能超过 128 字")
    private String pickupPointName;

    @Size(max = 255, message = "提货点地址不能超过 255 字")
    private String pickupPointAddress;
}
