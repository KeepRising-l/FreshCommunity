package com.freshcommunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("站内消息通知")
class NotificationApiTest extends ApiTestBase {

    private JsonNode notifications(String token) throws Exception {
        return perform(get("/api/notifications"), token, null).data();
    }

    private long unread(String token) throws Exception {
        return perform(get("/api/notifications/unread-count"), token, null).data().asLong();
    }

    private boolean hasType(JsonNode list, String type) {
        for (JsonNode n : list) {
            if (type.equals(n.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("成团时参团用户收到成团通知，未读数增加")
    void groupSuccessNotification() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);

        assertThat(unread(u1)).isZero();

        long gid = createGroupBuy(leader, productId, 1);
        long orderId = createOrder(u1, gid, 1);
        payOrder(u1, orderId);

        assertThat(hasType(notifications(u1), "GROUP_SUCCESS")).isTrue();
        assertThat(unread(u1)).isEqualTo(1);
    }

    @Test
    @DisplayName("核销成功后用户收到提货完成通知")
    void pickupDoneNotification() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);

        completedOrder(u1, leader, productId);

        assertThat(hasType(notifications(u1), "PICKUP_DONE")).isTrue();
    }

    @Test
    @DisplayName("管理员退款后用户收到退款通知")
    void refundNotification() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        String admin = login("admin", "123456");
        long productId = firstProductId(u1);

        long gid = createGroupBuy(leader, productId, 5);
        long orderId = createOrder(u1, gid, 1);
        payOrder(u1, orderId);
        perform(post("/api/orders/" + orderId + "/refund"), admin, null);

        assertThat(hasType(notifications(u1), "REFUNDED")).isTrue();
    }

    @Test
    @DisplayName("消息仅本人可见：不能标记他人消息为已读（400）")
    void cannotReadOthersNotification() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);

        long gid = createGroupBuy(leader, productId, 1);
        long orderId = createOrder(u1, gid, 1);
        payOrder(u1, orderId);
        long u1NotificationId = notifications(u1).get(0).path("id").asLong();

        // 造第二个用户
        ApiResult reg = perform(post("/api/auth/register"), null,
                Map.of("username", "intruder", "password", "abc123456"));
        String u2 = reg.data().path("token").asText();

        ApiResult r = perform(post("/api/notifications/" + u1NotificationId + "/read"), u2, null);

        assertThat(r.status()).isEqualTo(400);
        assertThat(unread(u1)).isEqualTo(1);
    }

    @Test
    @DisplayName("标记单条已读未读数减 1，全部已读后归零")
    void markReadBehaviour() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = firstProductId(u1);

        // target=2：两笔支付后达标成团，两笔订单各产生一条成团通知
        long gid = createGroupBuy(leader, productId, 2);
        long order1 = createOrder(u1, gid, 1);
        payOrder(u1, order1);
        long order2 = createOrder(u1, gid, 1);
        payOrder(u1, order2);

        long before = unread(u1);
        assertThat(before).isEqualTo(2);

        long firstId = notifications(u1).get(0).path("id").asLong();
        perform(post("/api/notifications/" + firstId + "/read"), u1, null);
        assertThat(unread(u1)).isEqualTo(before - 1);

        perform(post("/api/notifications/read-all"), u1, null);
        assertThat(unread(u1)).isZero();
    }

    @Test
    @DisplayName("推送流端点要求登录（未登录 401 统一 JSON）")
    void streamRequiresLogin() throws Exception {
        ApiResult r = perform(get("/api/notifications/stream"), null, null);
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.code()).isEqualTo(401);
    }
}
