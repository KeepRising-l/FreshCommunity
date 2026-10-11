package com.freshcommunity;

import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("认证与错误处理")
class AuthApiTest extends ApiTestBase {

    @Test
    @DisplayName("已注册账号登录成功并返回 token 与角色")
    void loginSuccess() throws Exception {
        ApiResult r = perform(post("/api/auth/login"), null,
                Map.of("username", "admin", "password", "123456"));

        assertThat(r.ok()).isTrue();
        assertThat(r.data().path("token").asText()).isNotBlank();
        assertThat(r.data().path("user").path("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("密码错误返回真正的 HTTP 401")
    void loginWrongPasswordReturns401() throws Exception {
        ApiResult r = perform(post("/api/auth/login"), null,
                Map.of("username", "user1", "password", "wrong"));

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.code()).isEqualTo(401);
    }

    @Test
    @DisplayName("自助注册成功：默认 USER 角色且直接返回 token")
    void registerSuccess() throws Exception {
        ApiResult r = perform(post("/api/auth/register"), null,
                Map.of("username", "newbie", "password", "abc123456"));

        assertThat(r.ok()).isTrue();
        assertThat(r.data().path("user").path("role").asText()).isEqualTo("USER");
        assertThat(r.data().path("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("重复用户名注册被拒（400）")
    void registerDuplicateUsernameRejected() throws Exception {
        ApiResult r = perform(post("/api/auth/register"), null,
                Map.of("username", "user1", "password", "abc123456"));

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.msg()).contains("用户名已存在");
    }

    @Test
    @DisplayName("不允许自助注册管理员（400）")
    void registerAdminRejected() throws Exception {
        ApiResult r = perform(post("/api/auth/register"), null,
                Map.of("username", "hacker", "password", "abc123456", "role", "ADMIN"));

        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("注册团长必须填写提货点名称（400）")
    void registerLeaderRequiresPickupPoint() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", "leader_new");
        body.put("password", "abc123456");
        body.put("role", "LEADER");

        ApiResult r = perform(post("/api/auth/register"), null, body);

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.msg()).contains("提货点");
    }

    @Test
    @DisplayName("未知路径返回 404（不再是 500）")
    void unknownPathReturns404() throws Exception {
        ApiResult r = perform(get("/api/auth/no-such-path"), null, null);

        assertThat(r.status()).isEqualTo(404);
    }

    @Test
    @DisplayName("请求方法不支持返回 405")
    void wrongMethodReturns405() throws Exception {
        ApiResult r = perform(get("/api/auth/login"), null, null);

        assertThat(r.status()).isEqualTo(405);
    }
}
