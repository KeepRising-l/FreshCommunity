package com.freshcommunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("团长端控制台：经营概览与最近核销记录")
class LeaderConsoleApiTest extends ApiTestBase {

    /** schema.sql 里三个基础账号的 BCrypt 哈希，密码均为 123456 */
    private static final String PASSWORD_HASH = "$2a$10$eYBgVzfZ/6nfpPiMGwzpq.LgZo3t3VMFs/5aJGVfJ718FKYswWaDi";

    private long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
    }

    private long createLeader(String username) {
        jdbcTemplate.update("INSERT INTO user (username, password, role) VALUES (?, ?, 'LEADER')",
                username, PASSWORD_HASH);
        return userId(username);
    }

    private long insertGroupBuy(long leaderId, String status, int deleted) {
        jdbcTemplate.update(
                "INSERT INTO group_buy (leader_id, title, target_count, deadline, status, member_count, deleted) " +
                        "VALUES (?, ?, 3, ?, ?, 0, ?)",
                leaderId, "团长端测试团", LocalDateTime.now().plusHours(2), status, deleted);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertOrder(long buyerId, long groupBuyId, BigDecimal amount, String status,
                             LocalDateTime updatedAt, int deleted) {
        Long productId = jdbcTemplate.queryForObject("SELECT id FROM product ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update(
                "INSERT INTO `order` (order_no, user_id, group_buy_id, product_id, quantity, amount, status, " +
                        "created_at, updated_at, deleted) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?)",
                "L" + UUID.randomUUID(), buyerId, groupBuyId, productId, amount, status,
                updatedAt, updatedAt, deleted);
    }

    private String insertCompletedOrder(long buyerId, long groupBuyId, BigDecimal amount, LocalDateTime at) {
        String orderNo = "L" + UUID.randomUUID();
        Long productId = jdbcTemplate.queryForObject("SELECT id FROM product ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update(
                "INSERT INTO `order` (order_no, user_id, group_buy_id, product_id, quantity, amount, status, " +
                        "created_at, updated_at, deleted) VALUES (?, ?, ?, ?, 1, ?, 'COMPLETED', ?, ?, 0)",
                orderNo, buyerId, groupBuyId, productId, amount, at, at);
        return orderNo;
    }

    // ---------- 经营概览 ----------

    @Test
    @DisplayName("经营概览只统计本人名下团购与订单")
    void summaryCountsOnlyOwnData() throws Exception {
        String leader1 = login("leader1", "123456");
        long leader1Id = userId("leader1");
        long leader2Id = createLeader("leader2");
        long buyer = userId("user1");

        long g1 = insertGroupBuy(leader1Id, "ONGOING", 0);
        insertOrder(buyer, g1, new BigDecimal("25.00"), "SUCCESS", LocalDateTime.now(), 0);
        insertOrder(buyer, g1, new BigDecimal("15.00"), "PENDING_PICKUP", LocalDateTime.now(), 0);
        insertOrder(buyer, g1, new BigDecimal("20.00"), "COMPLETED", LocalDateTime.now(), 0);
        insertOrder(buyer, g1, new BigDecimal("99.00"), "PENDING_PAYMENT", LocalDateTime.now(), 0);

        long g2 = insertGroupBuy(leader2Id, "ONGOING", 0);
        insertGroupBuy(leader2Id, "ONGOING", 0);
        insertOrder(buyer, g2, new BigDecimal("500.00"), "COMPLETED", LocalDateTime.now(), 0);

        JsonNode data = perform(get("/api/group-buys/mine/summary"), leader1, null).data();

        assertThat(data.path("ongoingGroups").asLong()).isEqualTo(1);
        // SUCCESS（已发码待上门）与历史状态 PENDING_PICKUP 都算待取货
        assertThat(data.path("pendingPickupOrders").asLong()).isEqualTo(2);
        assertThat(data.path("completedOrders").asLong()).isEqualTo(1);
        assertThat(data.path("totalGmv").decimalValue()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("真实成团链路：成团后计入待取货，核销后转为已核销")
    void summaryFollowsRealGroupSuccessFlow() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long productId = firstProductId(buyer);
        long gid = createGroupBuy(leader, productId, 1);
        long orderId = createOrder(buyer, gid, 1);
        payOrder(buyer, orderId);

        JsonNode afterSuccess = perform(get("/api/group-buys/mine/summary"), leader, null).data();
        assertThat(afterSuccess.path("pendingPickupOrders").asLong()).isEqualTo(1);
        assertThat(afterSuccess.path("completedOrders").asLong()).isZero();
        assertThat(afterSuccess.path("totalGmv").decimalValue()).isPositive();

        String code = jdbcTemplate.queryForObject(
                "SELECT pickup_code FROM `order` WHERE id = ?", String.class, orderId);
        perform(post("/api/orders/verify").param("pickupCode", code), leader, null);

        JsonNode afterVerify = perform(get("/api/group-buys/mine/summary"), leader, null).data();
        assertThat(afterVerify.path("pendingPickupOrders").asLong()).isZero();
        assertThat(afterVerify.path("completedOrders").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("还没成交时概览各项为 0，成交额不是空值")
    void summaryShowsZeroWhenNothingSold() throws Exception {
        String leader = login("leader1", "123456");
        long leaderId = userId("leader1");
        long gid = insertGroupBuy(leaderId, "ONGOING", 0);
        insertOrder(userId("user1"), gid, new BigDecimal("66.00"), "PENDING_PAYMENT", LocalDateTime.now(), 0);

        JsonNode data = perform(get("/api/group-buys/mine/summary"), leader, null).data();

        assertThat(data.path("pendingPickupOrders").asLong()).isZero();
        assertThat(data.path("completedOrders").asLong()).isZero();
        assertThat(data.path("totalGmv").isNumber()).isTrue();
        assertThat(data.path("totalGmv").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("普通用户不能查看团长经营概览（403）")
    void summaryRequiresLeader() throws Exception {
        ApiResult r = perform(get("/api/group-buys/mine/summary"), login("user1", "123456"), null);
        assertThat(r.status()).isEqualTo(403);
    }

    // ---------- 最近核销记录 ----------

    @Test
    @DisplayName("核销记录按核销时间倒序、最多 10 条，且不含其他团长与买家身份")
    void verifyHistoryListsRecentTenNewestFirst() throws Exception {
        String leader1 = login("leader1", "123456");
        long leader1Id = userId("leader1");
        long leader2Id = createLeader("leader2");
        long buyer = userId("user1");

        long mine = insertGroupBuy(leader1Id, "SUCCESS", 0);
        long other = insertGroupBuy(leader2Id, "SUCCESS", 0);
        LocalDateTime base = LocalDateTime.now().minusHours(20);
        String newest = null;
        for (int i = 1; i <= 12; i++) {
            String orderNo = insertCompletedOrder(buyer, mine, new BigDecimal("10.00"), base.plusHours(i));
            if (i == 12) {
                newest = orderNo;
            }
        }
        String otherLeaderOrder = insertCompletedOrder(buyer, other, new BigDecimal("88.00"), base.plusHours(13));

        JsonNode records = perform(get("/api/orders/verify-history"), leader1, null).data();

        assertThat(records.size()).isEqualTo(10);
        assertThat(records.get(0).path("orderNo").asText()).isEqualTo(newest);
        assertThat(records.get(0).has("verifiedAt")).isTrue();
        assertThat(records.get(0).has("userId")).isFalse();
        for (JsonNode record : records) {
            assertThat(record.path("orderNo").asText()).isNotEqualTo(otherLeaderOrder);
        }
    }

    @Test
    @DisplayName("从未核销时返回空数组")
    void verifyHistoryEmptyWhenNeverVerified() throws Exception {
        String leader = login("leader1", "123456");

        JsonNode records = perform(get("/api/orders/verify-history"), leader, null).data();

        assertThat(records.size()).isZero();
    }

    @Test
    @DisplayName("核销一笔后记录立即可见")
    void verifyHistoryReflectsNewVerification() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long orderId = completedOrder(buyer, leader, firstProductId(buyer));
        String orderNo = jdbcTemplate.queryForObject(
                "SELECT order_no FROM `order` WHERE id = ?", String.class, orderId);

        JsonNode records = perform(get("/api/orders/verify-history"), leader, null).data();

        assertThat(records.size()).isEqualTo(1);
        assertThat(records.get(0).path("orderNo").asText()).isEqualTo(orderNo);
        assertThat(records.get(0).path("quantity").asInt()).isEqualTo(1);
        assertThat(records.get(0).path("groupBuyTitle").asText()).isNotBlank();
    }

    @Test
    @DisplayName("普通用户不能查看核销记录（403）")
    void verifyHistoryRequiresLeader() throws Exception {
        ApiResult r = perform(get("/api/orders/verify-history"), login("user1", "123456"), null);
        assertThat(r.status()).isEqualTo(403);
    }
}
