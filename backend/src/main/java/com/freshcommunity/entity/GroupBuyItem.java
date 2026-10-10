package com.freshcommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("group_buy_item")
public class GroupBuyItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupBuyId;
    private Long productId;
    private BigDecimal priceSnapshot;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
