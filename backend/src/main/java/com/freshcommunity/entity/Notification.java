package com.freshcommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** GROUP_SUCCESS / GROUP_FAILED / ORDER_TIMEOUT / PICKUP_DONE / REFUNDED */
    private String type;
    private String title;
    private String content;
    private Long refOrderId;
    private Integer isRead;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
