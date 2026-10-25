package com.freshcommunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("AI 导购 · 需求订阅与智能提醒")
class SubscriptionApiTest extends ApiTestBase {

    @Test
    @DisplayName("规则解析：一句话需求解析为关键词+价格上限并回显")
    void subscribeParsesText() throws Exception {
        String u1 = login("user1", "123456");

        ApiResult r = perform(post("/api/subscriptions"), u1, Map.of("text", "想要 10 元以内的苹果"));

        assertThat(r.ok()).isTrue();
        assertThat(r.data().path("keyword").asText()).isEqualTo("苹果");
        assertThat(r.data().path("maxPrice").asDouble()).isEqualTo(10.0);
        assertThat(r.data().path("sourceText").asText()).contains("10 元以内的苹果");
    }

    @Test
    @DisplayName("解析预览：只返回条件不落库，订阅列表不受影响")
    void previewDoesNotCreate() throws Exception {
        String u1 = login("user1", "123456");

        ApiResult r = perform(post("/api/subscriptions/parse"), u1, Map.of("text", "想要 10 元以内的苹果"));

        assertThat(r.ok()).isTrue();
        assertThat(r.data().path("keyword").asText()).isEqualTo("苹果");
        assertThat(r.data().path("maxPrice").asDouble()).isEqualTo(10.0);
        assertThat(perform(get("/api/subscriptions"), u1, null).data().size()).isZero();
    }

    @Test
    @DisplayName("确认创建：以用户确认的条件落库，而非按原文重新解析")
    void createUsesConfirmedCondition() throws Exception {
        String u1 = login("user1", "123456");

        ApiResult preview = perform(post("/api/subscriptions/parse"), u1, Map.of("text", "想要 10 元以内的苹果"));
        assertThat(preview.data().path("keyword").asText()).isEqualTo("苹果");

        ApiResult created = perform(post("/api/subscriptions"), u1, Map.of(
                "text", "想要 10 元以内的苹果",
                "keyword", "红富士",
                "maxPrice", 25));

        assertThat(created.ok()).isTrue();
        assertThat(created.data().path("keyword").asText()).isEqualTo("红富士");
        assertThat(created.data().path("maxPrice").asDouble()).isEqualTo(25.0);

        JsonNode list = perform(get("/api/subscriptions"), u1, null).data();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("keyword").asText()).isEqualTo("红富士");
        assertThat(list.get(0).path("maxPrice").asDouble()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("无法解析的输入不创建订阅（400）")
    void subscribeRejectsUnparseable() throws Exception {
        String u1 = login("user1", "123456");

        ApiResult r = perform(post("/api/subscriptions"), u1, Map.of("text", "？？？"));

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.msg()).contains("换一种说法");
    }

    @Test
    @DisplayName("订阅列表仅本人可见；跨用户取消被拒")
    void listAndCancelOwnership() throws Exception {
        String u1 = login("user1", "123456");
        String u2 = register("sub_user2");

        long subId = createSubscription(u1, "10 元以内的苹果");

        assertThat(perform(get("/api/subscriptions"), u2, null).data().size()).isZero();
        assertThat(perform(get("/api/subscriptions"), u1, null).data().size()).isEqualTo(1);

        ApiResult deny = perform(delete("/api/subscriptions/" + subId), u2, null);
        assertThat(deny.status()).isEqualTo(400);

        assertThat(perform(delete("/api/subscriptions/" + subId), u1, null).ok()).isTrue();
        assertThat(perform(get("/api/subscriptions"), u1, null).data().size()).isZero();
    }

    @Test
    @DisplayName("开团命中订阅：订阅用户收到站内信（含商品名、价格与进度）")
    void newGroupBuyNotifiesSubscriber() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = productIdByName(u1, "红富士");

        createSubscription(u1, "25 元以内的红富士");
        createGroupBuy(leader, productId, 3);

        JsonNode noti = findNotify(u1, "SUBSCRIPTION_MATCH");
        assertThat(noti).isNotNull();
        assertThat(noti.path("content").asText())
                .contains("烟台红富士")
                .contains("19.90")
                .contains("还差 3 人");
        assertThat(countNotify(u1, "SUBSCRIPTION_MATCH")).isEqualTo(1);
    }

    @Test
    @DisplayName("不命中不打扰：价格超限或商品不符均不发送提醒")
    void missNoNotify() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = productIdByName(u1, "红富士");

        createSubscription(u1, "10 元以内的红富士");
        createSubscription(u1, "25 元以内的菠菜");

        createGroupBuy(leader, productId, 3);

        assertThat(countNotify(u1, "SUBSCRIPTION_MATCH")).isZero();
    }

    @Test
    @DisplayName("订阅时立即匹配：已有命中团购则订阅成功即收到提醒")
    void subscribeMatchesExisting() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = productIdByName(u1, "红富士");
        createGroupBuy(leader, productId, 2);

        ApiResult r = perform(post("/api/subscriptions"), u1, Map.of("text", "25 元以内的红富士"));

        assertThat(r.ok()).isTrue();
        assertThat(r.data().path("matchedCount").asInt()).isEqualTo(1);
        assertThat(countNotify(u1, "SUBSCRIPTION_MATCH")).isEqualTo(1);
    }

    @Test
    @DisplayName("双触发点不交叉：订阅已提醒的团不会被后续开团重复提醒，新团照常提醒")
    void noCrossTriggerDuplicate() throws Exception {
        String leader = login("leader1", "123456");
        String u1 = login("user1", "123456");
        long productId = productIdByName(u1, "红富士");

        createGroupBuy(leader, productId, 3);
        createSubscription(u1, "25 元以内的红富士");
        assertThat(countNotify(u1, "SUBSCRIPTION_MATCH")).isEqualTo(1);

        createGroupBuy(leader, productId, 3);

        assertThat(countNotify(u1, "SUBSCRIPTION_MATCH")).isEqualTo(2);
    }

    // ---------- 辅助 ----------

    private String register(String username) throws Exception {
        ApiResult r = perform(post("/api/auth/register"), null,
                Map.of("username", username, "password", "123456", "role", "USER"));
        if (!r.ok()) {
            throw new IllegalStateException("注册失败：" + r.body());
        }
        return r.data().path("token").asText();
    }

    private long createSubscription(String token, String text) throws Exception {
        ApiResult r = perform(post("/api/subscriptions"), token, Map.of("text", text));
        if (!r.ok()) {
            throw new IllegalStateException("订阅失败：" + r.body());
        }
        return r.data().path("id").asLong();
    }

    private long productIdByName(String token, String keyword) throws Exception {
        // 直接用检索接口的 keyword 参数（服务端模糊匹配），取命中结果的第一个
        for (JsonNode p : perform(get("/api/products").param("keyword", keyword), token, null)
                .data().path("records")) {
            if (p.path("name").asText().contains(keyword)) {
                return p.path("id").asLong();
            }
        }
        throw new IllegalStateException("商品未找到：" + keyword);
    }

    private JsonNode findNotify(String token, String type) throws Exception {
        for (JsonNode n : perform(get("/api/notifications"), token, null).data()) {
            if (type.equals(n.path("type").asText())) {
                return n;
            }
        }
        return null;
    }

    private int countNotify(String token, String type) throws Exception {
        int count = 0;
        for (JsonNode n : perform(get("/api/notifications"), token, null).data()) {
            if (type.equals(n.path("type").asText())) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("AI 即时推荐在无 key 环境明确报不可用（测试环境不外呼、不做假推荐）")
    void aiRecommendUnavailableWithoutKey() throws Exception {
        // application-test.yml 覆盖 ai.api-key=""：AiClient 不可用 → 400 + 明确提示
        var r = perform(post("/api/ai/recommend"), login("user1", "123456"),
                Map.of("text", "性价比高的苹果"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.msg()).contains("AI 服务暂时不可用");

        // 流式端点在注册 emitter 前同样前置校验（统一 400 JSON，而非 SSE error 事件）
        var rs = perform(post("/api/ai/recommend/stream"), login("user1", "123456"),
                Map.of("text", "性价比高的苹果"));
        assertThat(rs.status()).isEqualTo(400);
        assertThat(rs.msg()).contains("AI 服务暂时不可用");

        var blank = perform(post("/api/ai/recommend"), login("user1", "123456"),
                Map.of("text", "  "));
        assertThat(blank.status()).isEqualTo(400);
    }
}
