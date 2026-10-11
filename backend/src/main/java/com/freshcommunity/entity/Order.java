package com.freshcommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("`order`")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long groupBuyId;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private String status; // PENDING_PAYMENT/PENDING_GROUP/SUCCESS/PENDING_PICKUP/COMPLETED/CANCELLED/REFUNDED
    private String pickupCode;
    private LocalDateTime payTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    /** 是否已评价，非数据库字段，仅"我的订单"接口填充 */
    @TableField(exist = false)
    private Boolean reviewed;
}
