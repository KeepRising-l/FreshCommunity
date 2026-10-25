package com.freshcommunity.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料维护。
 * 头像（avatarUrl）全角色可写；提货点三项（名称/地址/照片）仅团长可写，
 * 普通用户提交提货点字段会被拒绝（见 user-roles spec）。
 * 图片 URL 必须是本站路径（/uploads/ 或 /images/ 前缀），由服务端校验。
 */
@Data
public class ProfileUpdateRequest {

    /** 头像地址（全角色可写） */
    @Size(max = 255, message = "头像地址不能超过 255 字")
    private String avatarUrl;

    @Size(max = 128, message = "提货点名称不能超过 128 字")
    private String pickupPointName;

    @Size(max = 255, message = "提货点地址不能超过 255 字")
    private String pickupPointAddress;

    @Size(max = 255, message = "提货点照片地址不能超过 255 字")
    private String pickupPointImageUrl;
}
