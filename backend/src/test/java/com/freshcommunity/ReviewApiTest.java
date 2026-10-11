package com.freshcommunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("评价体系")
class ReviewApiTest extends ApiTestBase {

    @Test
    @DisplayName("未完成（未核销）订单不可评价（400）")
    void reviewRejectedForUnfinishedOrder() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long gid = createGroupBuy(leader, productId, 5);
        long orderId = createOrder(u1, gid, 1);

        ApiResult r = perform(post("/api/reviews"), u1,
                Map.of("orderId", orderId, "productScore", 5, "leaderScore", 5));

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.msg()).contains("不可评价");
    }

    @Test
    @DisplayName("已完成订单可评价，且重复评价被拒")
    void reviewOnceThenRejected() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long orderId = completedOrder(u1, leader, productId);

        ApiResult first = perform(post("/api/reviews"), u1,
                Map.of("orderId", orderId, "productScore", 5, "leaderScore", 4, "content", "很新鲜"));
        assertThat(first.ok()).isTrue();

        ApiResult again = perform(post("/api/reviews"), u1,
                Map.of("orderId", orderId, "productScore", 3, "leaderScore", 3));
        assertThat(again.status()).isEqualTo(400);
        assertThat(again.msg()).contains("已评价");
    }

    @Test
    @DisplayName("评分超出 1-5 被拒（400）")
    void scoreOutOfRangeRejected() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long orderId = completedOrder(u1, leader, productId);

        ApiResult r = perform(post("/api/reviews"), u1,
                Map.of("orderId", orderId, "productScore", 9, "leaderScore", 4));

        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("评分汇总：平均分保留一位小数并给出条数")
    void ratingAggregation() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);

        long order1 = completedOrder(u1, leader, productId);
        long order2 = completedOrder(u1, leader, productId);
        perform(post("/api/reviews"), u1, Map.of("orderId", order1, "productScore", 5, "leaderScore", 5));
        perform(post("/api/reviews"), u1, Map.of("orderId", order2, "productScore", 4, "leaderScore", 3));

        JsonNode rating = perform(get("/api/reviews/product/" + productId), u1, null)
                .data().path("rating");

        assertThat(rating.path("count").asInt()).isEqualTo(2);
        assertThat(rating.path("avgScore").asDouble()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("无评价时不返回误导性的 0 分")
    void noReviewMeansNoScore() throws Exception {
        String u1 = login("user1", "123456");
        long otherProductId = perform(get("/api/products"), u1, null).data().get(1).path("id").asLong();

        JsonNode rating = perform(get("/api/reviews/product/" + otherProductId), u1, null)
                .data().path("rating");

        assertThat(rating.path("count").asInt()).isZero();
        assertThat(rating.path("avgScore").isNull()).isTrue();
    }

    @Test
    @DisplayName("我的订单接口标记 reviewed，评价后变为 true")
    void orderListExposesReviewedFlag() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long orderId = completedOrder(u1, leader, productId);

        JsonNode before = findOrder(u1, orderId);
        assertThat(before.path("reviewed").asBoolean()).isFalse();

        perform(post("/api/reviews"), u1, Map.of("orderId", orderId, "productScore", 5, "leaderScore", 5));

        JsonNode after = findOrder(u1, orderId);
        assertThat(after.path("reviewed").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("商品详情与团购详情返回评分汇总字段")
    void detailEndpointsExposeRating() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long gid = createGroupBuy(leader, productId, 1);
        long orderId = createOrder(u1, gid, 1);
        payOrder(u1, orderId);
        String code = jdbcTemplate.queryForObject(
                "SELECT pickup_code FROM `order` WHERE id = ?", String.class, orderId);
        perform(post("/api/orders/verify").param("pickupCode", code), leader, null);
        perform(post("/api/reviews"), u1, Map.of("orderId", orderId, "productScore", 5, "leaderScore", 4));

        JsonNode product = perform(get("/api/products/" + productId), u1, null).data();
        assertThat(product.path("avgScore").asDouble()).isEqualTo(5.0);
        assertThat(product.path("reviewCount").asInt()).isEqualTo(1);

        JsonNode groupBuy = perform(get("/api/group-buys/" + gid), u1, null).data();
        assertThat(groupBuy.path("leaderAvgScore").asDouble()).isEqualTo(4.0);
        assertThat(groupBuy.path("leaderReviewCount").asInt()).isEqualTo(1);
    }

    private JsonNode findOrder(String token, long orderId) throws Exception {
        for (JsonNode o : perform(get("/api/orders/mine"), token, null).data()) {
            if (o.path("id").asLong() == orderId) {
                return o;
            }
        }
        throw new IllegalStateException("订单未找到：" + orderId);
    }
}
