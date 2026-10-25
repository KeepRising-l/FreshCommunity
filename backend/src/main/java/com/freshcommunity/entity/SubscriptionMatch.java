package com.freshcommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅 × 团购匹配记录。(subscription_id, group_buy_id) 唯一键是
 * "同一订阅对同一团购只提醒一次"的数据库级闸门：各匹配路径以 INSERT IGNORE
 * 抢占，插入成功（首占）才发提醒。记录只增不删（取消订阅保留历史，
 * 逻辑删除字段仅保持全库约定一致）。
 */
@Data
@TableName("subscription_match")
public class SubscriptionMatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long subscriptionId;
    private Long groupBuyId;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
