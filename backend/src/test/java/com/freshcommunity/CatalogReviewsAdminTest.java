package com.freshcommunity;

import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 变更 catalog-reviews-admin：商品多图、同分类推荐、团长回复评价、订单 CSV 导出。
 */
@DisplayName("商品多图/同分类推荐/团长回复/CSV 导出")
class CatalogReviewsAdminTest extends ApiTestBase {

    @Test
    @DisplayName("商品多图：管理员保存图集后详情返回完整图列表，首图回写 imageUrl")
    void multiImageMaintenance() throws Exception {
        String admin = login("admin", "123456");

        var create = perform(post("/api/products"), admin, Map.of(
                "name", "多图测试橙", "category", "水果", "price", 15.0, "stock", 20,
                "images", List.of("/images/products/p05.jpg", "/images/products/p04.jpg")));
        assertThat(create.ok()).isTrue();
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = '多图测试橙'", Long.class);

        var detail = perform(get("/api/products/" + id), login("user1", "123456"), null).data();
        assertThat(detail.path("images").size()).isEqualTo(2);
        assertThat(detail.path("images").get(0).asText()).isEqualTo("/images/products/p05.jpg");
        // 首图回写：列表/卡片消费方直接读 imageUrl
        assertThat(detail.path("imageUrl").asText()).isEqualTo("/images/products/p05.jpg");
    }

    @Test
    @DisplayName("同分类推荐：同分类在售、排除自身、上限生效")
    void similarProducts() throws Exception {
        String user = login("user1", "123456");
        Long fuji = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = '烟台红富士'", Long.class);

        var r = perform(get("/api/products/" + fuji + "/similar").param("size", "2"), user, null);
        assertThat(r.ok()).isTrue();
        java.util.List<String> names = new java.util.ArrayList<>();
        r.data().forEach(item -> names.add(item.path("name").asText()));
        // 测试库（仅 schema 预置）水果类只有草莓一个同类，size=2 上限下返回全部同类
        assertThat(names).containsExactly("红颜草莓");
        assertThat(names).doesNotContain("烟台红富士");
        assertThat(names).allSatisfy(name -> {
            String cat = jdbcTemplate.queryForObject(
                    "SELECT category FROM product WHERE name = ?", String.class, name);
            assertThat(cat).isEqualTo("水果");
        });
    }

    @Test
    @DisplayName("团长回复评价：本人可回复可修改；他人/普通用户被拒；商品评价列表带回复")
    void leaderReplyFlow() throws Exception {
        String buyer = login("user1", "123456");
        String leader = login("leader1", "123456");
        long orderId = completedOrder(buyer, leader, firstProductId(buyer));
        // completedOrder 只造订单：买家先提交评价，才有可回复的记录
        var review = perform(post("/api/reviews"), buyer, Map.of(
                "orderId", orderId, "productScore", 5, "leaderScore", 5,
                "content", "苹果很新鲜，团长服务也好"));
        assertThat(review.ok()).isTrue();

        // 回复
        var reply = perform(post("/api/reviews/" + reviewIdOf(orderId) + "/reply"), leader,
                Map.of("content", "感谢支持，下次再来！"));
        assertThat(reply.ok()).isTrue();
        assertThat(reply.data().path("leaderReply").asText()).isEqualTo("感谢支持，下次再来！");

        // 修改
        var update = perform(post("/api/reviews/" + reviewIdOf(orderId) + "/reply"), leader,
                Map.of("content", "感谢支持，周末还有新团！"));
        assertThat(update.ok()).isTrue();
        assertThat(update.data().path("leaderReply").asText()).isEqualTo("感谢支持，周末还有新团！");

        // 其他团长不可回复（测试库无 leader2，注册临时团长）
        var reg = perform(post("/api/auth/register"), null, Map.of(
                "username", "reply_other_leader", "password", "pass123456",
                "role", "LEADER", "pickupPointName", "测试提货点"));
        assertThat(reg.ok()).isTrue();
        String otherLeaderToken = reg.data().path("token").asText();
        var other = perform(post("/api/reviews/" + reviewIdOf(orderId) + "/reply"), otherLeaderToken,
                Map.of("content", "越权回复"));
        assertThat(other.status()).isEqualTo(400);

        // 团长列表可见
        var mine = perform(get("/api/reviews/leader/mine"), leader, null).data();
        assertThat(mine.size()).isGreaterThanOrEqualTo(1);
        assertThat(mine.get(0).path("leaderReply").asText()).contains("周末还有新团");

        // 商品评价列表带回复
        Long productId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM review WHERE order_id = ?", Long.class, orderId);
        var productReviews = perform(get("/api/reviews/product/" + productId), buyer, null).data();
        assertThat(productReviews.path("reviews").get(0).path("leaderReply").asText())
                .contains("周末还有新团");
    }

    @Test
    @DisplayName("CSV 导出：BOM + 表头 + 行数据 + 筛选一致；非管理员 403")
    void csvExport() throws Exception {
        String admin = login("admin", "123456");

        // 测试库默认无订单：造一笔带逗号字段的订单（顺带验证 CSV 转义）
        long buyer = jdbcTemplate.queryForObject(
                "SELECT id FROM user WHERE username = 'user1'", Long.class);
        long leader = jdbcTemplate.queryForObject(
                "SELECT id FROM user WHERE username = 'leader1'", Long.class);
        long pid = jdbcTemplate.queryForObject(
                "SELECT id FROM product ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update(
                "INSERT INTO group_buy (leader_id, title, target_count, deadline, status, member_count) " +
                        "VALUES (?, '导出测试,团', 3, DATE_ADD(NOW(), INTERVAL 2 HOUR), 'ONGOING', 0)", leader);
        long gid = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO `order` (order_no, user_id, group_buy_id, product_id, quantity, amount, status) " +
                        "VALUES ('EXP001', ?, ?, ?, 2, 25.80, 'PENDING_GROUP')", buyer, gid, pid);
        jdbcTemplate.update(
                "INSERT INTO `order` (order_no, user_id, group_buy_id, product_id, quantity, amount, status) " +
                        "VALUES ('EXP002', ?, ?, ?, 1, 12.90, 'REFUNDED')", buyer, gid, pid);

        // CSV 是纯文本响应（带 BOM），不走 JSON 包装的 perform，直接读原文
        String body = mockMvc.perform(get("/api/admin/orders/export")
                        .header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body.charAt(0)).isEqualTo('\uFEFF');
        assertThat(body).contains("订单号,买家,商品,团购,数量,金额,状态,下单时间,支付时间,取货码");
        assertThat(body.split("\n").length).isGreaterThanOrEqualTo(2);
        assertThat(body).contains("EXP001");
        // 含逗号的字段整体加引号并双写引号（CSV 转义）
        assertThat(body).contains("\"导出测试,团\"");

        // 状态筛选一致性：REFUNDED 导出不含进行中状态行
        String rb = mockMvc.perform(get("/api/admin/orders/export").param("status", "REFUNDED")
                        .header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(rb).contains("REFUNDED");
        assertThat(rb).doesNotContain("PENDING_GROUP\n");

        // 403 走统一 JSON，可用 perform
        assertThat(perform(get("/api/admin/orders/export"), login("user1", "123456"), null).status())
                .isEqualTo(403);
    }

    private Long reviewIdOf(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM review WHERE order_id = ?", Long.class, orderId);
    }
}
