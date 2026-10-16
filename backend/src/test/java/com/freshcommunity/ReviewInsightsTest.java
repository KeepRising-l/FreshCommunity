package com.freshcommunity;

import com.freshcommunity.dto.ReviewVO;
import com.freshcommunity.entity.ReviewAnalysis;
import com.freshcommunity.mapper.ReviewAnalysisMapper;
import com.freshcommunity.mapper.ReviewMapper;
import com.freshcommunity.service.ReviewAnalysisService;
import com.freshcommunity.support.ApiTestBase;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("AI 导购 · 评价洞察")
class ReviewInsightsTest extends ApiTestBase {

    @Autowired
    private ReviewAnalysisService analysisService;

    @Autowired
    private ReviewAnalysisMapper analysisMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Test
    @DisplayName("规则分析：正面评价生成情感与标签，重复分析不产生重复记录")
    void analyzeOnceIdempotent() throws Exception {
        long reviewId = submitReview("很新鲜，也很实惠，到货快");

        analysisService.analyze(reviewId);
        analysisService.analyze(reviewId);

        List<ReviewAnalysis> all = analysisMapper.selectList(
                new LambdaQueryWrapper<ReviewAnalysis>().eq(ReviewAnalysis::getReviewId, reviewId));
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getSentiment()).isEqualTo("POSITIVE");
        assertThat(all.get(0).getTags()).contains("新鲜");
    }

    @Test
    @DisplayName("规则分析：负面评价识别为 NEGATIVE")
    void analyzeNegative() throws Exception {
        long reviewId = submitReview("有点贵，而且到货慢");

        analysisService.analyze(reviewId);

        ReviewAnalysis a = analysisMapper.selectOne(
                new LambdaQueryWrapper<ReviewAnalysis>().eq(ReviewAnalysis::getReviewId, reviewId));
        assertThat(a.getSentiment()).isEqualTo("NEGATIVE");
        assertThat(a.getTags()).contains("偏贵");
    }

    @Test
    @DisplayName("批量任务：有文字评价被分析，无文字评价不产生记录")
    void pendingBatchSkipsBlank() throws Exception {
        long withText = submitReview("非常新鲜好吃");
        long withoutText = submitReview(null);

        analysisService.analyzePending();

        assertThat(analysisMapper.selectCount(new LambdaQueryWrapper<ReviewAnalysis>()
                .eq(ReviewAnalysis::getReviewId, withText))).isEqualTo(1);
        assertThat(analysisMapper.selectCount(new LambdaQueryWrapper<ReviewAnalysis>()
                .eq(ReviewAnalysis::getReviewId, withoutText))).isZero();
    }

    @Test
    @DisplayName("口碑标签聚合：商品详情返回高频标签，无数据商品为空")
    void productDetailExposesTags() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);

        long reviewId = submitReview("很新鲜，很新鲜，实惠");
        // 该商品需与评价商品一致
        assertThat(reviewMapper.selectById(reviewId).getProductId()).isEqualTo(productId);
        analysisService.analyze(reviewId);

        ApiResult r = perform(get("/api/products/" + productId), u1, null);
        assertThat(r.ok()).isTrue();
        List<String> tags = new java.util.ArrayList<>();
        r.data().path("insightTags").forEach(n -> tags.add(n.asText()));
        assertThat(tags).contains("新鲜");

        // 无评价商品：insightTags 空
        long otherProduct = otherProductId(u1, productId);
        ApiResult r2 = perform(get("/api/products/" + otherProduct), u1, null);
        assertThat(r2.data().path("insightTags").isEmpty()).isTrue();
        assertThat(leader).isNotNull();
    }

    @Test
    @DisplayName("提醒附口碑摘要：有洞察时通知含摘要，无洞察时通知照常发送")
    void notifyIncludesInsightSummary() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);

        long reviewId = submitReview("很新鲜很实惠");
        analysisService.analyze(reviewId);

        // 订阅 → 已有进行中团购则立即匹配，通知应带口碑摘要
        long gid = createGroupBuy(leader, productId, 3);
        perform(post("/api/subscriptions"), u1, Map.of("text", "100 元以内的" + productName(u1, productId)));

        var noti = findNotify(u1, "SUBSCRIPTION_MATCH");
        assertThat(noti).isNotNull();
        assertThat(noti.path("content").asText()).contains("已有 1 人评价都说");
        assertThat(gid).isPositive();
    }

    // ---------- 辅助 ----------

    /** 造一条已完成订单并提交评价，返回 reviewId；content 为 null 表示无文字评价。 */
    private long submitReview(String content) throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long orderId = completedOrder(u1, leader, productId);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("orderId", orderId);
        body.put("productScore", 5);
        body.put("leaderScore", 5);
        if (content != null) {
            body.put("content", content);
        }
        ApiResult r = perform(post("/api/reviews"), u1, body);
        if (!r.ok()) {
            throw new IllegalStateException("评价失败：" + r.body());
        }
        return r.data().path("id").asLong();
    }

    private String productName(String token, long productId) throws Exception {
        return perform(get("/api/products/" + productId), token, null).data().path("name").asText();
    }

    private long otherProductId(String token, long exclude) throws Exception {
        for (var p : perform(get("/api/products"), token, null).data()) {
            if (p.path("id").asLong() != exclude) {
                return p.path("id").asLong();
            }
        }
        throw new IllegalStateException("无其他商品");
    }

    private com.fasterxml.jackson.databind.JsonNode findNotify(String token, String type) throws Exception {
        for (var n : perform(get("/api/notifications"), token, null).data()) {
            if (type.equals(n.path("type").asText())) {
                return n;
            }
        }
        return null;
    }
}
