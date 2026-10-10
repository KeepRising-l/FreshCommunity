package com.freshcommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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
}
