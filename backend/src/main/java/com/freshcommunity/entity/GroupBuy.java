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
@TableName("group_buy")
public class GroupBuy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long leaderId;
    private String title;
    private Integer targetCount;
    private LocalDateTime deadline;
    private String status; // ONGOING / SUCCESS / FAIL
    private Integer memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    /** 拼团价快照（来自 group_buy_item），仅 AI 推荐组装时内存填充，非数据库字段 */
    @TableField(exist = false)
    private BigDecimal itemPrice;
}
