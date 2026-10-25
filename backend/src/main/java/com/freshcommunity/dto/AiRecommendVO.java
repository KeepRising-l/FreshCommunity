package com.freshcommunity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 即时推荐结果：LLM 生成的回答文本 + 被点名商品的展示卡片。
 * 卡片挂进行中团购时带 groupBuyId（前端跳团购详情），否则挂商品（跳商品详情）。
 */
@Data
public class AiRecommendVO {
    /** LLM 回答文本（推荐 + 备选 + 小贴士，纯文本多行） */
    private String reply;
    /** 回答中被点名的商品卡片（最多 6 个） */
    private List<ItemVO> items;

    @Data
    public static class ItemVO {
        private Long productId;
        private String name;
        private String category;
        private BigDecimal price;
        /** 零售价（对比拼团价展示"立省"） */
        private BigDecimal originalPrice;
        private String imageUrl;
        private List<String> insightTags;

        /** 该商品的进行中团购（无则为 null，卡片跳商品详情） */
        private Long groupBuyId;
        private String groupTitle;
        private Integer memberCount;
        private Integer targetCount;
        /** 提货点名称（团长资料），下单确认展示 */
        private String pickupPointName;
    }
}
