package com.freshcommunity;

import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 个人中心（user-roles spec：修改密码 / 团长维护提货点）与 /me 对已删除账号的防护。
 */
@DisplayName("个人中心：修改密码与提货点维护")
class ProfileApiTest extends ApiTestBase {

    /** 测试库只有 schema.sql 预置的 3 个账号，改密用例的账号现场注册（初始密码 pass123456） */
    private String registerAndLogin(String username) throws Exception {
        var r = perform(post("/api/auth/register"), null, Map.of("username", username, "password", "pass123456"));
        assertThat(r.ok()).isTrue();
        return r.data().path("token").asText();
    }

    @Test
    @DisplayName("修改密码成功后：旧密码无法登录，新密码可登录")
    void changePasswordHappyPath() throws Exception {
        String token = registerAndLogin("profile_pw_user");

        var r = perform(post("/api/auth/change-password"), token,
                Map.of("oldPassword", "pass123456", "newPassword", "newpass99"));
        assertThat(r.ok()).isTrue();

        assertThat(perform(post("/api/auth/login"), null,
                Map.of("username", "profile_pw_user", "password", "pass123456")).status()).isEqualTo(401);
        assertThat(perform(post("/api/auth/login"), null,
                Map.of("username", "profile_pw_user", "password", "newpass99")).ok()).isTrue();
    }

    @Test
    @DisplayName("旧密码错误 / 新旧相同 / 新密码过短均被拒（400）")
    void changePasswordValidations() throws Exception {
        String token = registerAndLogin("profile_valid_user");

        var wrongOld = perform(post("/api/auth/change-password"), token,
                Map.of("oldPassword", "wrong-old", "newPassword", "abc12345"));
        assertThat(wrongOld.status()).isEqualTo(400);
        assertThat(wrongOld.msg()).contains("旧密码错误");

        var same = perform(post("/api/auth/change-password"), token,
                Map.of("oldPassword", "pass123456", "newPassword", "pass123456"));
        assertThat(same.status()).isEqualTo(400);
        assertThat(same.msg()).contains("不能与旧密码相同");

        var tooShort = perform(post("/api/auth/change-password"), token,
                Map.of("oldPassword", "pass123456", "newPassword", "abc"));
        assertThat(tooShort.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("团长修改提货点后，其团购详情即时展示新提货点")
    void leaderUpdatesPickupPoint() throws Exception {
        String leader = login("leader1", "123456");
        String user = login("user1", "123456");
        long gid = createGroupBuy(leader, firstProductId(user), 5);

        var r = perform(put("/api/auth/profile"), leader, Map.of(
                "pickupPointName", "新提货点便利店",
                "pickupPointAddress", "新地址 88 号"));
        assertThat(r.ok()).isTrue();
        assertThat(r.data().path("pickupPointName").asText()).isEqualTo("新提货点便利店");

        var detail = perform(get("/api/group-buys/" + gid), user, null).data();
        assertThat(detail.path("pickupPointName").asText()).isEqualTo("新提货点便利店");
        assertThat(detail.path("pickupPointAddress").asText()).isEqualTo("新地址 88 号");
    }

    @Test
    @DisplayName("提货点名称不能为空；普通用户不可维护提货点")
    void pickupPointValidations() throws Exception {
        var blank = perform(put("/api/auth/profile"), login("leader1", "123456"),
                Map.of("pickupPointName", "  "));
        assertThat(blank.status()).isEqualTo(400);
        assertThat(blank.msg()).contains("不能为空");

        var notLeader = perform(put("/api/auth/profile"), login("user1", "123456"),
                Map.of("pickupPointName", "随便填"));
        assertThat(notLeader.status()).isEqualTo(400);
        assertThat(notLeader.msg()).contains("仅团长");
    }

    @Test
    @DisplayName("/me 对已删除账号返回 401 而非 500（回归：此前空指针）")
    void meReturns401ForDeletedUser() throws Exception {
        var reg = perform(post("/api/auth/register"), null,
                Map.of("username", "ghostuser", "password", "pass123456"));
        assertThat(reg.ok()).isTrue();
        String token = reg.data().path("token").asText();

        // 注意：先删再查。若删除前先调一次 /me，MyBatis 一级缓存会让删除后的
        // selectById 命中旧结果（jdbcTemplate 的 UPDATE 不清 MyBatis 缓存），测不出真实行为。
        jdbcTemplate.update("UPDATE user SET deleted = 1 WHERE username = 'ghostuser'");
        var after = perform(get("/api/auth/me"), token, null);
        assertThat(after.status()).isEqualTo(401);
        assertThat(after.code()).isEqualTo(401);
    }
}
