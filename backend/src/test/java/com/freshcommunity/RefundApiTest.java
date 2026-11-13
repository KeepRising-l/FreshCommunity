package com.freshcommunity;

import com.freshcommunity.entity.Order;
import com.freshcommunity.job.ScheduledJobs;
import com.freshcommunity.service.MockPayServiceImpl;
import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefundApiTest extends ApiTestBase {
    @SpyBean
    private MockPayServiceImpl payment;
    @Autowired
    private ScheduledJobs jobs;

    @Test
    void cancellationInvokesSimulatedRefundAndFailureRollsBack() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long productId = firstProductId(buyer);
        long groupId = createGroupBuy(leader, productId, 3);
        long orderId = createOrder(buyer, groupId, 1);
        assertThat(payOrder(buyer, orderId).ok()).isTrue();
        long stock = productStock(buyer, productId);
        doReturn(false).when(payment).refund(any(Order.class));

        assertThat(perform(post("/api/orders/" + orderId + "/cancel"), buyer, null).status()).isEqualTo(500);
        verify(payment, times(1)).refund(any(Order.class));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM `order` WHERE id = ?", String.class, orderId))
                .isEqualTo("PENDING_GROUP");
        assertThat(groupBuyDetail(buyer, groupId).path("memberCount").asInt()).isEqualTo(1);
        assertThat(productStock(buyer, productId)).isEqualTo(stock);
    }

    @Test
    void adminRefundOfUnpaidOrderDoesNotCallPaymentRefund() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        String admin = login("admin", "123456");
        long groupId = createGroupBuy(leader, firstProductId(buyer), 3);
        long orderId = createOrder(buyer, groupId, 1);

        assertThat(perform(post("/api/orders/" + orderId + "/refund"), admin, null).ok()).isTrue();
        verify(payment, never()).refund(any(Order.class));
    }

    @Test
    void adminRefundOfPaidOrderCallsSimulatedRefund() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        String admin = login("admin", "123456");
        long groupId = createGroupBuy(leader, firstProductId(buyer), 3);
        long orderId = createOrder(buyer, groupId, 1);
        assertThat(payOrder(buyer, orderId).ok()).isTrue();

        assertThat(perform(post("/api/orders/" + orderId + "/refund"), admin, null).ok()).isTrue();
        verify(payment, times(1)).refund(any(Order.class));
        assertThat(groupBuyDetail(buyer, groupId).path("memberCount").asInt()).isZero();
    }

    @Test
    void expiredGroupRefundsEveryPaidOrder() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long productId = firstProductId(buyer);
        long groupId = createGroupBuy(leader, productId, 3);
        long first = createOrder(buyer, groupId, 1);
        long second = createOrder(buyer, groupId, 1);
        assertThat(payOrder(buyer, first).ok()).isTrue();
        assertThat(payOrder(buyer, second).ok()).isTrue();
        jdbcTemplate.update("UPDATE group_buy SET deadline = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id = ?", groupId);

        jobs.processExpiredGroupBuys();

        verify(payment, times(1)).refund(argThat(order -> order != null && order.getId().equals(first)));
        verify(payment, times(1)).refund(argThat(order -> order != null && order.getId().equals(second)));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `order` WHERE group_buy_id = ? AND status = 'REFUNDED'",
                Integer.class, groupId)).isEqualTo(2);
    }

    @Test
    void failedGroupRefundRollsBackIfPaymentRefundFails() throws Exception {
        String leader = login("leader1", "123456");
        String buyer = login("user1", "123456");
        long productId = firstProductId(buyer);
        long groupId = createGroupBuy(leader, productId, 3);
        long orderId = createOrder(buyer, groupId, 1);
        assertThat(payOrder(buyer, orderId).ok()).isTrue();
        long stock = productStock(buyer, productId);
        jdbcTemplate.update("UPDATE group_buy SET deadline = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id = ?", groupId);
        doReturn(false).when(payment).refund(any(Order.class));

        try {
            assertThatThrownBy(() -> jobs.processExpiredGroupBuys()).isInstanceOf(IllegalStateException.class);
            verify(payment, times(1)).refund(argThat(order -> order != null && order.getId().equals(orderId)));
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM group_buy WHERE id = ?", String.class, groupId))
                    .isEqualTo("ONGOING");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM `order` WHERE id = ?", String.class, orderId))
                    .isEqualTo("PENDING_GROUP");
            assertThat(productStock(buyer, productId)).isEqualTo(stock);
        } finally {
            // 该类不启用测试事务；避免回滚后的到期团影响后续测试。
            jdbcTemplate.update("UPDATE group_buy SET deadline = DATE_ADD(NOW(), INTERVAL 1 DAY) WHERE id = ?", groupId);
        }
    }
}
