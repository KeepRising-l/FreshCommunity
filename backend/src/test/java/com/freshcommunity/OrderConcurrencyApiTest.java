package com.freshcommunity;

import com.freshcommunity.support.ApiTestBase;
import com.freshcommunity.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 并发请求需要独立事务，测试数据由 ApiTestBase 在专用测试库中初始化。
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderConcurrencyApiTest extends ApiTestBase {
    @Autowired
    private OrderMapper orders;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    @Test
    void creationWaitsForGroupResultAndRejectsAfterSuccess() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long productId = firstProductId(buyer);
        long groupId = createGroupBuy(leader, productId, 1);
        long initialStock = productStock(buyer, productId);
        var executor = Executors.newSingleThreadExecutor();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var update = connection.prepareStatement("UPDATE group_buy SET status = 'SUCCESS' WHERE id = ?")) {
                update.setLong(1, groupId);
                update.executeUpdate();
            }
            var result = executor.submit(() -> perform(post("/api/orders")
                    .param("groupBuyId", String.valueOf(groupId)).param("quantity", "1"), buyer, null));
            assertThatThrownBy(() -> result.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            connection.commit();
            assertThat(result.get(10, TimeUnit.SECONDS).status()).isEqualTo(400);
            assertThat(productStock(buyer, productId)).isEqualTo(initialStock);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `order` WHERE group_buy_id = ?",
                    Integer.class, groupId)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousPaymentsFromSameUserCountOnce() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long groupId = createGroupBuy(leader, firstProductId(buyer), 2);
        long first = createOrder(buyer, groupId, 1);
        long second = createOrder(buyer, groupId, 1);
        assertThat(concurrently(() -> payOrder(buyer, first).status(), () -> payOrder(buyer, second).status()))
                .containsExactlyInAnyOrder(200, 200);
        assertThat(jdbcTemplate.queryForObject("SELECT member_count FROM group_buy WHERE id = ?", Integer.class, groupId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, groupId))
                .isEqualTo("ONGOING");
    }

    @Test
    void simultaneousSamePickupCodeOnlyOneDatabaseWriteSucceeds() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long groupId = createGroupBuy(leader, firstProductId(buyer), 3);
        long first = createOrder(buyer, groupId, 1);
        long second = createOrder(buyer, groupId, 1);

        assertThat(concurrently(() -> writeSameCode(first), () -> writeSameCode(second)))
                .containsExactlyInAnyOrder(1, 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order` WHERE group_buy_id = ? AND pickup_code = '876543'",
                Integer.class, groupId)).isEqualTo(1);
    }

    private int writeSameCode(long orderId) {
        try {
            return jdbcTemplate.update("UPDATE `order` SET pickup_code = '876543' WHERE id = ?", orderId);
        } catch (DuplicateKeyException expected) {
            return 0;
        }
    }

    @Test
    void groupOrderLookupSeesPaymentsCommittedAfterTransactionSnapshot() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long groupId = createGroupBuy(leader, firstProductId(buyer), 2);
        long orderId = createOrder(buyer, groupId, 1);
        var snapshotTaken = new CountDownLatch(1);
        var paymentCommitted = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                return transaction.execute(status -> {
                    jdbcTemplate.queryForObject("SELECT status FROM `order` WHERE id = ?", String.class, orderId);
                    snapshotTaken.countDown();
                    try {
                        if (!paymentCommitted.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试超时");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return orders.findByGroupBuyAndStatus(groupId, "PENDING_GROUP").size();
                });
            });
            assertThat(snapshotTaken.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(payOrder(buyer, orderId).ok()).isTrue();
            paymentCommitted.countDown();
            assertThat(result.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            paymentCommitted.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousPaymentsThatReachTargetBothReceivePickupCodes() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long groupId = createGroupBuy(leader, firstProductId(buyer), 2);
        long first = createOrder(buyer, groupId, 1);
        long second = createOrder(leader, groupId, 1);

        assertThat(concurrently(() -> payOrder(buyer, first).status(), () -> payOrder(leader, second).status()))
                .containsExactlyInAnyOrder(200, 200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order` WHERE group_buy_id = ? AND status = 'SUCCESS' AND pickup_code IS NOT NULL",
                Integer.class, groupId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT pickup_code) FROM `order` WHERE group_buy_id = ?", Integer.class, groupId))
                .isEqualTo(2);
    }

    @Test
    void simultaneousPaymentsOnLastPlaceOnlyOneCanPay() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long groupId = createGroupBuy(leader, firstProductId(buyer), 1);
        long first = createOrder(buyer, groupId, 1);
        long second = createOrder(buyer, groupId, 1);

        List<Integer> results = concurrently(
                () -> payOrder(buyer, first).status(), () -> payOrder(buyer, second).status());

        assertThat(results).containsExactlyInAnyOrder(200, 400);
        assertThat(jdbcTemplate.queryForObject("SELECT member_count FROM group_buy WHERE id = ?", Integer.class, groupId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order` WHERE group_buy_id = ? AND status = 'SUCCESS' AND pickup_code IS NOT NULL",
                Integer.class, groupId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order` WHERE group_buy_id = ? AND status = 'PENDING_PAYMENT'",
                Integer.class, groupId)).isEqualTo(1);
    }

    @Test
    void simultaneousPickupVerificationOnlyOneSucceeds() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long groupId = createGroupBuy(leader, firstProductId(buyer), 1);
        long orderId = createOrder(buyer, groupId, 1);
        assertThat(payOrder(buyer, orderId).ok()).isTrue();
        String code = jdbcTemplate.queryForObject("SELECT pickup_code FROM `order` WHERE id = ?", String.class, orderId);

        Callable<Integer> request = () -> perform(post("/api/orders/verify").param("pickupCode", code), leader, null).status();
        assertThat(concurrently(request, request)).containsExactlyInAnyOrder(200, 400);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM `order` WHERE id = ?", String.class, orderId))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE ref_order_id = ? AND type = 'PICKUP_DONE'",
                Integer.class, orderId)).isEqualTo(1);
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> { start.await(); return first.call(); });
            var b = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
