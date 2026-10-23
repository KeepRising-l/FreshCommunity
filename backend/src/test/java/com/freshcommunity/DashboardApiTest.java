package com.freshcommunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("管理看板：成交额口径、状态分布、7 日趋势与权限")
class DashboardApiTest extends ApiTestBase {

    private long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
    }

    private long firstProductIdFromDb() {
        return jdbcTemplate.queryForObject("SELECT id FROM product ORDER BY id LIMIT 1", Long.class);
    }

    private long insertGroupBuy(long leaderId, String status, int deleted) {
        jdbcTemplate.update(
                "INSERT INTO group_buy (leader_id, title, target_count, deadline, status, member_count, deleted) " +
                        "VALUES (?, ?, 3, ?, ?, 0, ?)",
                leaderId, "看板测试团", LocalDateTime.now().plusHours(2), status, deleted);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertOrder(long userId, long groupBuyId, BigDecimal amount, String status,
                             LocalDateTime createdAt, int deleted) {
        jdbcTemplate.update(
                "INSERT INTO `order` (order_no, user_id, group_buy_id, product_id, quantity, amount, status, " +
                        "created_at, updated_at, deleted) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?)",
                "T" + UUID.randomUUID(), userId, groupBuyId, firstProductIdFromDb(), amount, status,
                createdAt, createdAt, deleted);
    }

    @Test
    @DisplayName("今日成交额只计已支付口径：待支付 / 已取消 / 已退款均不计入")
    void todayGmvCountsOnlyPaidOrders() throws Exception {
        String admin = login("admin", "123456");
        long buyer = userId("user1");
        long gid = insertGroupBuy(userId("leader1"), "ONGOING", 0);
        LocalDateTime now = LocalDateTime.now();

        insertOrder(buyer, gid, new BigDecimal("20.00"), "PENDING_GROUP", now, 0);
        insertOrder(buyer, gid, new BigDecimal("15.50"), "SUCCESS", now, 0);
        insertOrder(buyer, gid, new BigDecimal("10.00"), "PENDING_PICKUP", now, 0);
        insertOrder(buyer, gid, new BigDecimal("5.00"), "COMPLETED", now, 0);
        insertOrder(buyer, gid, new BigDecimal("99.00"), "PENDING_PAYMENT", now, 0);
        insertOrder(buyer, gid, new BigDecimal("88.00"), "CANCELLED", now, 0);
        insertOrder(buyer, gid, new BigDecimal("77.00"), "REFUNDED", now, 0);

        JsonNode data = perform(get("/api/admin/dashboard"), admin, null).data();

        assertThat(data.path("todayGmv").decimalValue()).isEqualByComparingTo("50.50");
        assertThat(data.path("todayOrders").asLong()).isEqualTo(7);
        assertThat(data.path("todaySuccessGroups").asLong()).isZero();
    }

    @Test
    @DisplayName("今日无成交时展示 0，而不是空值")
    void emptyTodayShowsZero() throws Exception {
        String admin = login("admin", "123456");

        JsonNode data = perform(get("/api/admin/dashboard"), admin, null).data();

        assertThat(data.path("todayGmv").isNumber()).isTrue();
        assertThat(data.path("todayGmv").decimalValue()).isEqualByComparingTo("0");
        assertThat(data.path("todayOrders").asLong()).isZero();
        assertThat(data.path("orderStatusDistribution").size()).isZero();
    }

    @Test
    @DisplayName("逻辑删除的订单不进订单数、成交额与状态分布（回归：手写 SQL 曾漏 deleted 条件）")
    void deletedOrdersExcluded() throws Exception {
        String admin = login("admin", "123456");
        long buyer = userId("user1");
        long gid = insertGroupBuy(userId("leader1"), "ONGOING", 0);
        LocalDateTime now = LocalDateTime.now();

        insertOrder(buyer, gid, new BigDecimal("30.00"), "PENDING_GROUP", now, 0);
        insertOrder(buyer, gid, new BigDecimal("40.00"), "PENDING_GROUP", now, 1);

        JsonNode data = perform(get("/api/admin/dashboard"), admin, null).data();

        assertThat(data.path("todayOrders").asLong()).isEqualTo(1);
        assertThat(data.path("todayGmv").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(data.path("orderStatusDistribution").size()).isEqualTo(1);
        assertThat(data.path("orderStatusDistribution").get(0).path("status").asText()).isEqualTo("PENDING_GROUP");
        assertThat(data.path("orderStatusDistribution").get(0).path("count").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("订单状态分布与数据库按状态分组的计数一致")
    void statusDistributionMatchesDatabase() throws Exception {
        String admin = login("admin", "123456");
        long buyer = userId("user1");
        long gid = insertGroupBuy(userId("leader1"), "ONGOING", 0);
        LocalDateTime now = LocalDateTime.now();

        insertOrder(buyer, gid, new BigDecimal("10.00"), "PENDING_PAYMENT", now, 0);
        insertOrder(buyer, gid, new BigDecimal("10.00"), "PENDING_PAYMENT", now, 0);
        insertOrder(buyer, gid, new BigDecimal("10.00"), "COMPLETED", now, 0);
        insertOrder(buyer, gid, new BigDecimal("10.00"), "COMPLETED", now, 0);
        insertOrder(buyer, gid, new BigDecimal("10.00"), "COMPLETED", now, 0);
        insertOrder(buyer, gid, new BigDecimal("10.00"), "REFUNDED", now, 0);

        Map<String, Long> actual = new HashMap<>();
        for (JsonNode item : perform(get("/api/admin/dashboard"), admin, null).data().path("orderStatusDistribution")) {
            actual.put(item.path("status").asText(), item.path("count").asLong());
        }

        Map<String, Long> expected = new HashMap<>();
        jdbcTemplate.query(
                "SELECT status, COUNT(*) AS c FROM `order` WHERE deleted = 0 GROUP BY status",
                rs -> {
                    expected.put(rs.getString("status"), rs.getLong("c"));
                });

        assertThat(actual).isEqualTo(expected);
        assertThat(actual).containsEntry("PENDING_PAYMENT", 2L)
                .containsEntry("COMPLETED", 3L)
                .containsEntry("REFUNDED", 1L);
    }

    @Test
    @DisplayName("近 7 日趋势连续补零，窗口外订单不计入")
    void trendIsContinuousSevenDays() throws Exception {
        String admin = login("admin", "123456");
        long buyer = userId("user1");
        long gid = insertGroupBuy(userId("leader1"), "ONGOING", 0);
        LocalDateTime now = LocalDateTime.now();

        insertOrder(buyer, gid, new BigDecimal("10.00"), "PENDING_GROUP", now.minusDays(1), 0);
        insertOrder(buyer, gid, new BigDecimal("20.00"), "COMPLETED", now.minusDays(5), 0);
        // 8 天前的订单：不在窗口内
        insertOrder(buyer, gid, new BigDecimal("999.00"), "COMPLETED", now.minusDays(7), 0);

        JsonNode trend = perform(get("/api/admin/dashboard"), admin, null).data().path("trend7d");

        assertThat(trend.size()).isEqualTo(7);
        LocalDate today = LocalDate.now();
        long totalOrders = 0;
        BigDecimal totalGmv = BigDecimal.ZERO;
        for (int i = 0; i < 7; i++) {
            JsonNode point = trend.get(i);
            assertThat(point.path("date").asText()).isEqualTo(today.minusDays(6 - i).toString());
            totalOrders += point.path("orders").asLong();
            totalGmv = totalGmv.add(point.path("gmv").decimalValue());
        }
        assertThat(totalOrders).isEqualTo(2);
        assertThat(totalGmv).isEqualByComparingTo("30.00");
        assertThat(trend.get(0).path("date").asText()).isNotEqualTo(today.minusDays(7).toString());
    }

    @Test
    @DisplayName("平台规模概览：注册用户数随注册增长，进行中团购按状态计数")
    void platformScaleOverview() throws Exception {
        String admin = login("admin", "123456");
        long usersBefore = perform(get("/api/admin/dashboard"), admin, null).data().path("totalUsers").asLong();

        perform(post("/api/auth/register"), null, Map.of("username", "scaleuser", "password", "abc123456"));
        insertGroupBuy(userId("leader1"), "ONGOING", 0);
        insertGroupBuy(userId("leader1"), "ONGOING", 0);
        insertGroupBuy(userId("leader1"), "SUCCESS", 0);
        insertGroupBuy(userId("leader1"), "ONGOING", 1);

        JsonNode data = perform(get("/api/admin/dashboard"), admin, null).data();

        assertThat(data.path("totalUsers").asLong()).isEqualTo(usersBefore + 1);
        assertThat(data.path("ongoingGroupBuys").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("普通用户与团长都不能访问看板（403）")
    void dashboardRequiresAdmin() throws Exception {
        assertThat(perform(get("/api/admin/dashboard"), login("user1", "123456"), null).status()).isEqualTo(403);
        assertThat(perform(get("/api/admin/dashboard"), login("leader1", "123456"), null).status()).isEqualTo(403);
    }
}
