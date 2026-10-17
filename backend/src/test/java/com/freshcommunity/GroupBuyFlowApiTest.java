package com.freshcommunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.freshcommunity.job.ScheduledJobs;
import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("团购生命周期、库存与退款守卫")
class GroupBuyFlowApiTest extends ApiTestBase {

    @Autowired
    private ScheduledJobs scheduledJobs;

    @Test
    @DisplayName("团购列表返回团长与提货点信息（回归：曾返回空）")
    void listContainsLeaderAndPickupPoint() throws Exception {
        String leader = login("leader1", "123456");
        String token = login("user1", "123456");
        // 测试库只跑 schema.sql（不含 seed.sql），需自行造一场进行中的团购
        long gid = createGroupBuy(leader, firstProductId(token), 3);

        JsonNode first = perform(get("/api/group-buys"), token, null).data().get(0);

        assertThat(first).isNotNull();
        assertThat(first.path("id").asLong()).isEqualTo(gid);
        assertThat(first.path("leaderName").asText()).isEqualTo("leader1");
        assertThat(first.path("pickupPointName").asText()).isEqualTo("幸福小区便利店");
        assertThat(first.path("pickupPointAddress").asText()).isEqualTo("幸福小区东门 101 号");
    }

    @Test
    @DisplayName("团购列表与详情透出所挂商品、拼团价与原价（回归：曾无价格字段）")
    void listAndDetailExposeProductAndPrice() throws Exception {
        String leader = login("leader1", "123456");
        String token = login("user1", "123456");
        long productId = firstProductId(token);
        long gid = createGroupBuy(leader, productId, 3);

        // 商品原价取自 product，拼团价取自 group_buy_item.price_snapshot
        JsonNode product = perform(get("/api/products/" + productId), token, null).data();
        String productName = product.path("name").asText();
        double originalPrice = product.path("price").asDouble();

        JsonNode listItem = perform(get("/api/group-buys"), token, null).data().get(0);
        assertThat(listItem.path("productId").asLong()).isEqualTo(productId);
        assertThat(listItem.path("productName").asText()).isEqualTo(productName);
        assertThat(listItem.path("originalPrice").asDouble()).isEqualTo(originalPrice);
        // 拼团价必须存在且不高于原价
        assertThat(listItem.path("price").isNumber()).isTrue();
        assertThat(listItem.path("price").asDouble()).isLessThanOrEqualTo(originalPrice);

        JsonNode detail = groupBuyDetail(token, gid);
        assertThat(detail.path("productId").asLong()).isEqualTo(productId);
        assertThat(detail.path("productName").asText()).isEqualTo(productName);
        assertThat(detail.path("price").asDouble()).isEqualTo(listItem.path("price").asDouble());
        assertThat(detail.path("originalPrice").asDouble()).isEqualTo(originalPrice);
    }

    @Test
    @DisplayName("开团可指定拼团价：落库为指定值，订阅匹配与提醒按该价判断（回归：拼团价曾被写死为零售价）")
    void createGroupBuyWithCustomPrice() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        double retailPrice = perform(get("/api/products/" + productId), u1, null)
                .data().path("price").asDouble();
        double groupPrice = Math.round(retailPrice * 50) / 100.0;

        long gid = createGroupBuyWithPrice(leader, productId, 3, groupPrice);

        assertThat(groupBuyDetail(u1, gid).path("price").asDouble()).isEqualTo(groupPrice);

        // 订阅条件按「拼团价」而非零售价匹配：上限介于两者之间时应命中
        double maxPrice = (groupPrice + retailPrice) / 2;
        ApiResult sub = perform(post("/api/subscriptions"), u1,
                java.util.Map.of("text", String.format("%.2f 元以内的%s", maxPrice, productName(u1, productId))));
        assertThat(sub.data().path("matchedCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("拼团价校验：高于零售价或非正数时拒绝创建")
    void customPriceValidation() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        double retailPrice = perform(get("/api/products/" + productId), u1, null)
                .data().path("price").asDouble();

        ApiResult tooHigh = perform(post("/api/group-buys"), leader, java.util.Map.of(
                "title", "价格过高的团",
                "productId", productId,
                "targetCount", 3,
                "price", retailPrice + 1,
                "deadline", java.time.LocalDateTime.now().plusHours(2).toString()));
        assertThat(tooHigh.status()).isEqualTo(400);
        assertThat(tooHigh.msg()).contains("零售价");

        ApiResult nonPositive = perform(post("/api/group-buys"), leader, java.util.Map.of(
                "title", "零元团",
                "productId", productId,
                "targetCount", 3,
                "price", 0,
                "deadline", java.time.LocalDateTime.now().plusHours(2).toString()));
        assertThat(nonPositive.status()).isEqualTo(400);
    }

    private long createGroupBuyWithPrice(String token, long productId, int target, double price) throws Exception {
        ApiResult r = perform(post("/api/group-buys"), token, java.util.Map.of(
                "title", "指定拼团价的团购",
                "productId", productId,
                "targetCount", target,
                "price", price,
                "deadline", java.time.LocalDateTime.now().plusHours(2).toString()));
        if (!r.ok()) {
            throw new IllegalStateException("创建团购失败：" + r.body());
        }
        return r.data().asLong();
    }

    private String productName(String token, long productId) throws Exception {
        return perform(get("/api/products/" + productId), token, null).data().path("name").asText();
    }

    @Test
    @DisplayName("取消待成团订单回退团购人数，不再产生虚假成团")
    void cancelOrderRollsBackMemberCount() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long gid = createGroupBuy(leader, productId, 2);

        long orderA = createOrder(u1, gid, 1);
        payOrder(u1, orderA);
        assertThat(groupBuyDetail(u1, gid).path("memberCount").asInt()).isEqualTo(1);

        perform(post("/api/orders/" + orderA + "/cancel"), u1, null);
        assertThat(groupBuyDetail(u1, gid).path("memberCount").asInt()).isZero();

        // A 取消后 B 支付（有效人数仅 1），不应成团
        long orderB = createOrder(u1, gid, 1);
        payOrder(u1, orderB);

        JsonNode detail = groupBuyDetail(u1, gid);
        assertThat(detail.path("memberCount").asInt()).isEqualTo(1);
        assertThat(detail.path("status").asText()).isEqualTo("ONGOING");
    }

    @Test
    @DisplayName("库存不足时下单被拒")
    void orderRejectedWhenStockInsufficient() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long gid = createGroupBuy(leader, productId, 5);
        long stock = productStock(u1, productId);

        ApiResult r = perform(post("/api/orders")
                .param("groupBuyId", String.valueOf(gid))
                .param("quantity", String.valueOf(stock + 1)), u1, null);

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.msg()).contains("库存不足");
    }

    @Test
    @DisplayName("管理员重复退款被拒，库存不会反复回补（回归）")
    void duplicateRefundRejected() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        String admin = login("admin", "123456");
        long productId = firstProductId(u1);
        long gid = createGroupBuy(leader, productId, 5);

        long orderId = createOrder(u1, gid, 2);
        payOrder(u1, orderId);
        long stockBeforeRefund = productStock(u1, productId);

        ApiResult first = perform(post("/api/orders/" + orderId + "/refund"), admin, null);
        assertThat(first.ok()).isTrue();
        assertThat(productStock(u1, productId)).isEqualTo(stockBeforeRefund + 2);

        ApiResult second = perform(post("/api/orders/" + orderId + "/refund"), admin, null);
        assertThat(second.status()).isEqualTo(400);
        assertThat(productStock(u1, productId)).isEqualTo(stockBeforeRefund + 2);
    }

    @Test
    @DisplayName("待支付超 30 分钟被定时任务自动关单并回补库存（回归）")
    void pendingPaymentOrderAutoClosed() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long gid = createGroupBuy(leader, productId, 5);

        long orderId = createOrder(u1, gid, 1);
        long stockAfterOrder = productStock(u1, productId);

        jdbcTemplate.update(
                "UPDATE `order` SET created_at = DATE_SUB(NOW(), INTERVAL 40 MINUTE) WHERE id = ?", orderId);
        scheduledJobs.closePendingPaymentOrders();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM `order` WHERE id = ?", String.class, orderId);
        assertThat(status).isEqualTo("CANCELLED");
        assertThat(productStock(u1, productId)).isEqualTo(stockAfterOrder + 1);
    }

    @Test
    @DisplayName("非团长不能发起团购（403）")
    void createGroupBuyRequiresLeaderRole() throws Exception {
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);

        ApiResult r = perform(post("/api/group-buys"), u1, java.util.Map.of(
                "title", "越权开团", "productId", productId, "targetCount", 1,
                "deadline", java.time.LocalDateTime.now().plusHours(1).toString()));

        assertThat(r.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("团长可查看自己团购的订单列表（回归：曾查成团长自己的订单）")
    void leaderCanListGroupOrders() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);
        long gid = createGroupBuy(leader, productId, 3);
        long orderId = createOrder(u1, gid, 1);

        ApiResult r = perform(get("/api/group-buys/" + gid + "/orders"), leader, null);

        assertThat(r.ok()).isTrue();
        assertThat(r.data().size()).isEqualTo(1);
        assertThat(r.data().get(0).path("id").asLong()).isEqualTo(orderId);
        // 订单属于下单的用户，而不是团长本人
        assertThat(r.data().get(0).path("userId").asLong()).isEqualTo(3L);
    }

    @Test
    @DisplayName("其他团长不能查看别人的团购订单（400）")
    void otherLeaderCannotListGroupOrders() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long gid = createGroupBuy(leader, firstProductId(u1), 3);

        ApiResult reg = perform(post("/api/auth/register"), null, java.util.Map.of(
                "username", "leader2", "password", "abc123456",
                "role", "LEADER", "pickupPointName", "另一个提货点"));
        String otherLeader = reg.data().path("token").asText();

        ApiResult r = perform(get("/api/group-buys/" + gid + "/orders"), otherLeader, null);

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.msg()).contains("无权");
    }

    @Test
    @DisplayName("普通用户不能查看团购订单（403）")
    void userCannotListGroupOrders() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long gid = createGroupBuy(leader, firstProductId(u1), 3);

        ApiResult r = perform(get("/api/group-buys/" + gid + "/orders"), u1, null);

        assertThat(r.status()).isEqualTo(403);
    }
}
