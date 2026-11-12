package com.freshcommunity;

import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.Order;
import com.freshcommunity.mapper.*;
import com.freshcommunity.service.NotificationService;
import com.freshcommunity.service.OrderService;
import com.freshcommunity.service.PayService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderConcurrencyTest {
    @Test
    void simultaneousVerificationOnlyOneSucceedsAndNotifies() throws Exception {
        OrderMapper orders = mock(OrderMapper.class);
        GroupBuyMapper groups = mock(GroupBuyMapper.class);
        NotificationService notifications = mock(NotificationService.class);
        OrderService service = new OrderService(orders, groups, mock(GroupBuyItemMapper.class),
                mock(ProductMapper.class), mock(UserMapper.class), mock(PayService.class), notifications);
        Order order = new Order();
        order.setId(1L);
        order.setUserId(3L);
        order.setGroupBuyId(2L);
        order.setStatus("SUCCESS");
        GroupBuy group = new GroupBuy();
        group.setLeaderId(7L);
        CountDownLatch bothRead = new CountDownLatch(2);
        when(orders.findByPickupCode("123456")).thenAnswer(invocation -> {
            bothRead.countDown();
            if (!bothRead.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试超时");
            return order;
        });
        when(groups.selectById(2L)).thenReturn(group);
        AtomicInteger updates = new AtomicInteger();
        when(orders.updateStatus(1L, "COMPLETED", "SUCCESS"))
                .thenAnswer(invocation -> updates.getAndIncrement() == 0 ? 1 : 0);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> attemptVerify(service));
            var second = executor.submit(() -> attemptVerify(service));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            verify(notifications, times(1)).notify(eq(3L), eq(NotificationService.TYPE_PICKUP_DONE),
                    anyString(), anyString(), eq(1L));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attemptVerify(OrderService service) {
        try {
            service.verifyPickupCode(7L, "123456");
            return true;
        } catch (IllegalArgumentException expected) {
            return false;
        }
    }

    @Test
    void pickupCodeCollisionRetriesBeforeNotifying() {
        OrderMapper orders = mock(OrderMapper.class);
        GroupBuyMapper groups = mock(GroupBuyMapper.class);
        NotificationService notifications = mock(NotificationService.class);
        OrderService service = new OrderService(orders, groups, mock(GroupBuyItemMapper.class),
                mock(ProductMapper.class), mock(UserMapper.class), mock(PayService.class), notifications);
        Order order = new Order();
        order.setId(1L);
        order.setUserId(3L);
        when(groups.trySuccess(2L)).thenReturn(1);
        when(orders.findByGroupBuyAndStatus(2L, "PENDING_GROUP")).thenReturn(List.of(order));
        when(orders.assignPickupCode(eq(1L), anyString()))
                .thenThrow(new DuplicateKeyException("取货码冲突"))
                .thenReturn(1);

        service.tryGroupSuccess(2L);

        verify(orders, times(2)).assignPickupCode(eq(1L), matches("\\d{6}"));
        verify(notifications, times(1)).notify(eq(3L), eq(NotificationService.TYPE_GROUP_SUCCESS),
                anyString(), anyString(), eq(1L));
    }
}
