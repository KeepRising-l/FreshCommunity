package com.freshcommunity;

import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 订单详情与订单信息补全（order-lifecycle spec：订单详情 / 订单信息补全 / 管理端订单列表）。
 */
@DisplayName("订单详情、信息补全与管理端订单列表")
class OrderDetailApiTest extends ApiTestBase {

    private long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
    }

    /** 造一笔「待成团」订单并返回订单 id */
    private long pendingGroupOrder() throws Exception {
        String leader = login("leader1", "123456");
        String user = login("user1", "123456");
        long gid = createGroupBuy(leader, firstProductId(user), 5);
        return createOrder(user, gid, 2);
    }

    /** 测试库只有 schema.sql 预置的 3 个账号，其余买家现场注册 */
    private String registerAndLogin(String username) throws Exception {
        var r = perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register"),
                null, java.util.Map.of("username", username, "password", "pass123456"));
        assertThat(r.ok()).isTrue();
        return r.data().path("token").asText();
    }

    @Test
    @DisplayName("我的订单列表自带商品名与提货点（订单信息补全）")
    void mineCarriesDisplayFields() throws Exception {
        pendingGroupOrder();
        String user = login("user1", "123456");

        var orders = perform(get("/api/orders/mine"), user, null).data();

        assertThat(orders.size()).isGreaterThanOrEqualTo(1);
        var o = orders.get(0);
        assertThat(o.path("productName").asText()).isEqualTo("红颜草莓");
        assertThat(o.path("pickupPointName").asText()).isEqualTo("幸福小区便利店");
        assertThat(o.path("groupBuyTitle").asText()).isEqualTo("自动化测试团购");
        assertThat(o.path("leaderName").asText()).isEqualTo("leader1");
        assertThat(o.has("orderNo")).isTrue();
    }

    @Test
    @DisplayName("订单详情：本人可查且字段完整；他人按不存在拒绝；管理员可查任意")
    void detailPermission() throws Exception {
        long orderId = pendingGroupOrder();
        String user = login("user1", "123456");

        var own = perform(get("/api/orders/" + orderId), user, null);
        assertThat(own.ok()).isTrue();
        assertThat(own.data().path("productName").asText()).isEqualTo("红颜草莓");
        assertThat(own.data().path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(own.data().has("pickupPointAddress")).isTrue();

        var other = perform(get("/api/orders/" + orderId), registerAndLogin("detail_other_user"), null);
        assertThat(other.status()).isEqualTo(400);
        assertThat(other.code()).isEqualTo(400);
        assertThat(other.msg()).contains("订单不存在");

        var admin = perform(get("/api/orders/" + orderId), login("admin", "123456"), null);
        assertThat(admin.ok()).isTrue();
        assertThat(admin.data().path("buyerName")).isEmpty(); // /detail 不返回买家字段（管理列表才有）
    }

    @Test
    @DisplayName("商品被逻辑删除后订单列表与详情不报错，商品名为空")
    void deletedProductStillRenders() throws Exception {
        long orderId = pendingGroupOrder();
        jdbcTemplate.update("UPDATE product SET deleted = 1 WHERE id = " +
                "(SELECT product_id FROM `order` WHERE id = ?)", orderId);
        String user = login("user1", "123456");

        var mine = perform(get("/api/orders/mine"), user, null);
        assertThat(mine.ok()).isTrue();

        var detail = perform(get("/api/orders/" + orderId), user, null);
        assertThat(detail.ok()).isTrue();
        assertThat(detail.data().path("productName").isNull()).isTrue();
    }

    @Test
    @DisplayName("管理端订单列表：按订单号/买家用户名搜索、状态筛选与分页")
    void adminOrdersSearchAndPaging() throws Exception {
        long orderId = pendingGroupOrder();
        String admin = login("admin", "123456");
        String orderNo = jdbcTemplate.queryForObject(
                "SELECT order_no FROM `order` WHERE id = ?", String.class, orderId);

        var byNo = perform(get("/api/admin/orders").param("keyword", orderNo), admin, null).data();
        assertThat(byNo.path("total").asLong()).isEqualTo(1);
        assertThat(byNo.path("records").get(0).path("orderNo").asText()).isEqualTo(orderNo);
        assertThat(byNo.path("records").get(0).path("buyerName").asText()).isEqualTo("user1");
        assertThat(byNo.path("records").get(0).path("productName").asText()).isEqualTo("红颜草莓");

        var byBuyer = perform(get("/api/admin/orders").param("keyword", "user1"), admin, null).data();
        assertThat(byBuyer.path("total").asLong()).isGreaterThanOrEqualTo(1);

        var byStatus = perform(get("/api/admin/orders").param("status", "PENDING_PAYMENT"), admin, null).data();
        assertThat(byStatus.path("total").asLong()).isGreaterThanOrEqualTo(1);
        for (var rec : byStatus.path("records")) {
            assertThat(rec.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        }

        var paged = perform(get("/api/admin/orders").param("page", "1").param("size", "1"), admin, null).data();
        assertThat(paged.path("records").size()).isEqualTo(1);
        assertThat(paged.path("size").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("管理端订单列表仅管理员可见")
    void adminOrdersRequiresAdmin() throws Exception {
        assertThat(perform(get("/api/admin/orders"), login("user1", "123456"), null).status()).isEqualTo(403);
        assertThat(perform(get("/api/admin/orders"), login("leader1", "123456"), null).status()).isEqualTo(403);
    }
}
