package com.freshcommunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.freshcommunity.service.SubscriptionService;
import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 订阅匹配可靠性（ai-subscription spec：去重闸门 / 补偿扫描 / 提醒可跳转）。
 * seed 里 user1 有一条苹果团历史，测试库 schema 预置 3 商品无订阅。
 */
@DisplayName("订阅匹配：唯一闸门去重、补偿扫描与提醒跳转引用")
class SubscriptionMatchReliabilityTest extends ApiTestBase {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private com.freshcommunity.mapper.UserSubscriptionMapper subscriptionMapper;

    private long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
    }

    private long notificationCount(long uid, String type) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = ? AND deleted = 0",
                Long.class, uid, type);
        return n == null ? 0 : n;
    }

    /** 用 user1 创建一条订阅并返回订阅 id（创建时的立即匹配被计入计数） */
    private long subscribe(String text) throws Exception {
        String token = login("user1", "123456");
        var r = perform(post("/api/subscriptions"), token, java.util.Map.of("text", text));
        assertThat(r.ok()).isTrue();
        return r.data().path("id").asLong();
    }

    @Test
    @DisplayName("双路径去重：订阅命中进行中团后，开团路径再跑一次不重复提醒")
    void dedupAcrossPaths() throws Exception {
        // 测试库只有 schema 预置商品（无 seed 团购），先造一个进行中的菠菜团
        String leader = login("leader1", "123456");
        long pid = jdbcTemplate.queryForObject("SELECT id FROM product WHERE name = '新鲜菠菜'", Long.class);
        long gid = createGroupBuy(leader, pid, 5);

        long subId = subscribe("10 元以内的菠菜"); // 菠菜 4.50 ≤ 10，命中该团
        long uid = userId("user1");
        assertThat(notificationCount(uid, "SUBSCRIPTION_MATCH")).isEqualTo(1);

        // 开团路径：对该团再跑一次 matchNewGroupBuy，唯一键闸门应拦截
        int matched = subscriptionService.matchNewGroupBuy(gid);

        assertThat(matched).isZero();
        assertThat(notificationCount(uid, "SUBSCRIPTION_MATCH")).isEqualTo(1);
        assertThat(subId).isPositive();
    }

    @Test
    @DisplayName("重复触发去重：同一团连跑两次 matchNewGroupBuy，第二次 matched=0 且提醒数不变")
    void dedupSamePathRepeat() throws Exception {
        // 先开团（此时订阅还不存在，内部匹配为空转），再插一条订阅绕过创建时的立即匹配。
        // 必须经 MyBatis 插入：jdbcTemplate 的 UPDATE 不清 MyBatis 一级缓存，
        // 而 createGroupBuy 内部已经用同样的空条件查过一次订阅列表，再查会命中旧缓存。
        String leader = login("leader1", "123456");
        long pid = jdbcTemplate.queryForObject("SELECT id FROM product WHERE name = '新鲜菠菜'", Long.class);
        long gid = createGroupBuy(leader, pid, 5);

        long uid = userId("user1");
        com.freshcommunity.entity.UserSubscription sub = new com.freshcommunity.entity.UserSubscription();
        sub.setUserId(uid);
        sub.setKeyword("菠菜");
        sub.setSourceText("菠菜");
        subscriptionMapper.insert(sub);

        int first = subscriptionService.matchNewGroupBuy(gid);
        int second = subscriptionService.matchNewGroupBuy(gid);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(notificationCount(uid, "SUBSCRIPTION_MATCH")).isEqualTo(1);
    }

    @Test
    @DisplayName("补偿扫描：绕过同步路径直插订阅，被补齐且二次扫描不重复")
    void compensationCatchesMissedPair() {
        long uid = userId("user1");
        // 模拟"订阅与开团并发互相错过"：直插订阅 + 直插进行中团购，两条同步路径都没跑
        jdbcTemplate.update(
                "INSERT INTO user_subscription (user_id, keyword, max_price, source_text) VALUES (?, '草莓', 50, '草莓')",
                uid);
        jdbcTemplate.update(
                "INSERT INTO group_buy (leader_id, title, target_count, deadline, status, member_count) " +
                        "VALUES ((SELECT id FROM user WHERE username='leader1'), '草莓补偿团', 3, ?, 'ONGOING', 0)",
                LocalDateTime.now().plusHours(2));
        long gid = jdbcTemplate.queryForObject(
                "SELECT id FROM group_buy WHERE title = '草莓补偿团'", Long.class);
        long pid = jdbcTemplate.queryForObject("SELECT id FROM product WHERE name = '红颜草莓'", Long.class);
        jdbcTemplate.update("INSERT INTO group_buy_item (group_buy_id, product_id, price_snapshot) VALUES (?, ?, 29.90)",
                gid, pid);

        assertThat(notificationCount(uid, "SUBSCRIPTION_MATCH")).isZero();

        int first = subscriptionService.compensateMissed();
        int second = subscriptionService.compensateMissed();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(notificationCount(uid, "SUBSCRIPTION_MATCH")).isEqualTo(1);
    }

    @Test
    @DisplayName("补偿扫描不产出不符合条件的对（关键词不匹配 / 超价格上限）")
    void compensationSkipsNonMatches() {
        long uid = userId("user1");
        jdbcTemplate.update(
                "INSERT INTO user_subscription (user_id, keyword, max_price, source_text) VALUES (?, '土豆', NULL, '土豆')",
                uid);
        jdbcTemplate.update(
                "INSERT INTO user_subscription (user_id, keyword, max_price, source_text) VALUES (?, '草莓', 10, '草莓高价上限')",
                uid);
        jdbcTemplate.update(
                "INSERT INTO group_buy (leader_id, title, target_count, deadline, status, member_count) " +
                        "VALUES ((SELECT id FROM user WHERE username='leader1'), '草莓贵价团', 3, ?, 'ONGOING', 0)",
                LocalDateTime.now().plusHours(2));
        long gid = jdbcTemplate.queryForObject(
                "SELECT id FROM group_buy WHERE title = '草莓贵价团'", Long.class);
        long pid = jdbcTemplate.queryForObject("SELECT id FROM product WHERE name = '红颜草莓'", Long.class);
        jdbcTemplate.update("INSERT INTO group_buy_item (group_buy_id, product_id, price_snapshot) VALUES (?, ?, 29.90)",
                gid, pid);

        int compensated = subscriptionService.compensateMissed();

        assertThat(compensated).isZero();
        assertThat(notificationCount(uid, "SUBSCRIPTION_MATCH")).isZero();
    }

    @Test
    @DisplayName("提醒携带团购引用（refGroupBuyId）且无订单引用；通知列表接口透出该字段")
    void matchNotificationCarriesGroupRef() throws Exception {
        long uid = userId("user1");
        jdbcTemplate.update(
                "INSERT INTO user_subscription (user_id, keyword, max_price, source_text) VALUES (?, '菠菜', NULL, '菠菜')",
                uid);
        String leader = login("leader1", "123456");
        long pid = jdbcTemplate.queryForObject("SELECT id FROM product WHERE name = '新鲜菠菜'", Long.class);
        long gid = createGroupBuy(leader, pid, 5);
        subscriptionService.matchNewGroupBuy(gid);

        long ref = jdbcTemplate.queryForObject(
                "SELECT ref_group_buy_id FROM notification WHERE user_id = ? AND type = 'SUBSCRIPTION_MATCH' " +
                        "ORDER BY id DESC LIMIT 1", Long.class, uid);
        Long orderRef = jdbcTemplate.queryForObject(
                "SELECT ref_order_id FROM notification WHERE user_id = ? AND type = 'SUBSCRIPTION_MATCH' " +
                        "ORDER BY id DESC LIMIT 1", Long.class, uid);
        assertThat(ref).isEqualTo(gid);
        assertThat(orderRef).isNull();

        JsonNode notifications = perform(get("/api/notifications"), login("user1", "123456"), null).data();
        JsonNode match = null;
        for (JsonNode n : notifications) {
            if ("SUBSCRIPTION_MATCH".equals(n.path("type").asText())) {
                match = n;
                break;
            }
        }
        assertThat(match).isNotNull();
        assertThat(match.path("refGroupBuyId").asLong()).isEqualTo(gid);
        assertThat(match.path("refOrderId").isNull()).isTrue();
    }
}
