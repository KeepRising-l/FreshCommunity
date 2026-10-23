package com.freshcommunity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 核销记录：只暴露订单与团购信息，不含买家身份。 */
@Data
public class VerifyRecordVO {
    private String orderNo;
    private String groupBuyTitle;
    private Integer quantity;
    private BigDecimal amount;
    private LocalDateTime verifiedAt;
}
