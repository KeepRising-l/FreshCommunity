package com.freshcommunity.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 集成测试基类：真实 Spring 上下文 + MockMvc + 真实 MySQL（独立测试库 freshcommunity_test）。
 *
 * - 测试库由 db/schema.sql 初始化（库名替换为测试库），每个测试类启动时重建一次
 * - 测试方法带 @Transactional，结束后回滚，互不污染
 * - 数据库账号可用 DB_USER / DB_PASSWORD 环境变量覆盖，默认 root / 1234
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ApiTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected Environment env;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** HTTP 状态码 + 响应体（统一 {code,msg,data} 包装） */
    public record ApiResult(int status, JsonNode body) {
        public int code() {
            return body.path("code").asInt();
        }

        public String msg() {
            return body.path("msg").asText();
        }

        public JsonNode data() {
            return body.path("data");
        }

        public boolean ok() {
            return status == 200 && code() == 200;
        }
    }

    @BeforeAll
    void applyTestSchema() throws Exception {
        String url = env.getProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username");
        String pass = env.getProperty("spring.datasource.password");

        int q = url.indexOf('?');
        String params = q >= 0 ? url.substring(q) : "";
        String head = q >= 0 ? url.substring(0, q) : url;
        String dbName = head.substring(head.lastIndexOf('/') + 1);
        String serverUrl = head.substring(0, head.lastIndexOf('/') + 1) + params;

        Path schemaPath = Paths.get("..", "db", "schema.sql").toAbsolutePath().normalize();
        String script = Files.readString(schemaPath, StandardCharsets.UTF_8)
                .replace("freshcommunity", dbName);

        try (Connection conn = DriverManager.getConnection(serverUrl, user, pass)) {
            ScriptUtils.executeSqlScript(conn,
                    new EncodedResource(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)),
                            StandardCharsets.UTF_8));
        }
    }

    // ---------- 通用请求 ----------

    protected ApiResult perform(MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.contentType("application/json").content(objectMapper.writeValueAsString(body));
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = content.isEmpty() ? objectMapper.nullNode() : objectMapper.readTree(content);
        return new ApiResult(result.getResponse().getStatus(), json);
    }

    protected String login(String username, String password) throws Exception {
        ApiResult r = perform(post("/api/auth/login"), null,
                Map.of("username", username, "password", password));
        if (!r.ok()) {
            throw new IllegalStateException("登录失败：" + username + " -> " + r.body());
        }
        return r.data().path("token").asText();
    }

    // ---------- 业务流程辅助 ----------

    protected long firstProductId(String token) throws Exception {
        return perform(get("/api/products"), token, null).data().get(0).path("id").asLong();
    }

    protected long productStock(String token, long productId) throws Exception {
        return perform(get("/api/products/" + productId), token, null).data().path("stock").asLong();
    }

    protected long createGroupBuy(String token, long productId, int target) throws Exception {
        ApiResult r = perform(post("/api/group-buys"), token, Map.of(
                "title", "自动化测试团购",
                "productId", productId,
                "targetCount", target,
                "deadline", LocalDateTime.now().plusHours(2).toString()));
        if (!r.ok()) {
            throw new IllegalStateException("创建团购失败：" + r.body());
        }
        return r.data().asLong();
    }

    protected long createOrder(String token, long groupBuyId, int quantity) throws Exception {
        ApiResult r = perform(post("/api/orders")
                .param("groupBuyId", String.valueOf(groupBuyId))
                .param("quantity", String.valueOf(quantity)), token, null);
        if (!r.ok()) {
            throw new IllegalStateException("下单失败：" + r.body());
        }
        return r.data().path("id").asLong();
    }

    protected ApiResult payOrder(String token, long orderId) throws Exception {
        return perform(post("/api/orders/" + orderId + "/pay"), token, null);
    }

    protected JsonNode groupBuyDetail(String token, long groupBuyId) throws Exception {
        return perform(get("/api/group-buys/" + groupBuyId), token, null).data();
    }

    /** 造一笔"已完成"订单：开 target=1 的团 → 下单 → 支付（立即成团）→ 团长核销。 */
    protected long completedOrder(String buyerToken, String leaderToken, long productId) throws Exception {
        long gid = createGroupBuy(leaderToken, productId, 1);
        long orderId = createOrder(buyerToken, gid, 1);
        payOrder(buyerToken, orderId);
        String code = jdbcTemplate.queryForObject(
                "SELECT pickup_code FROM `order` WHERE id = ?", String.class, orderId);
        perform(post("/api/orders/verify").param("pickupCode", code), leaderToken, null);
        return orderId;
    }
}
