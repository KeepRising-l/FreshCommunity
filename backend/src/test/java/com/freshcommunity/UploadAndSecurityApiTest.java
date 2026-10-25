package com.freshcommunity;

import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 上传接口加固（扩展名白名单 + contentType 双校验）与安全过滤链的统一 401/403 JSON 返回。
 */
@DisplayName("上传加固与统一 401/403 返回结构")
class UploadAndSecurityApiTest extends ApiTestBase {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};

    @Test
    @DisplayName("合法 PNG 上传成功并返回 /uploads 路径")
    void uploadPngSucceeds() throws Exception {
        String admin = login("admin", "123456");

        var r = perform(multipart("/api/upload").file(
                new MockMultipartFile("file", "logo.png", "image/png", PNG_BYTES)), admin, null);

        assertThat(r.ok()).isTrue();
        assertThat(r.data().asText()).startsWith("/uploads/");
    }

    @Test
    @DisplayName("非图片扩展名被拒：.txt / 无扩展名 / 扩展名图片但 contentType 非图片")
    void uploadRejectsNonImage() throws Exception {
        String admin = login("admin", "123456");

        var txt = perform(multipart("/api/upload").file(
                new MockMultipartFile("file", "evil.txt", "text/plain", "hello".getBytes())), admin, null);
        assertThat(txt.status()).isEqualTo(400);
        assertThat(txt.msg()).contains("图片");

        // 回归：无扩展名时旧实现 substring(-1) 直接 500
        var noExt = perform(multipart("/api/upload").file(
                new MockMultipartFile("file", "noext", "image/png", PNG_BYTES)), admin, null);
        assertThat(noExt.status()).isEqualTo(400);

        var fake = perform(multipart("/api/upload").file(
                new MockMultipartFile("file", "fake.png", "text/plain", "not image".getBytes())), admin, null);
        assertThat(fake.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("上传仅管理员可用：普通用户 403")
    void uploadRequiresAdmin() throws Exception {
        var r = perform(multipart("/api/upload").file(
                new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES)),
                login("user1", "123456"), null);
        assertThat(r.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("未认证请求返回 HTTP 401 + 统一 JSON（回归：此前空体 403）")
    void unauthenticatedReturnsUnified401() throws Exception {
        var noToken = perform(get("/api/products"), null, null);
        assertThat(noToken.status()).isEqualTo(401);
        assertThat(noToken.code()).isEqualTo(401);
        assertThat(noToken.msg()).isEqualTo("未登录或登录已过期");

        var badToken = perform(get("/api/products"), "not-a-jwt", null);
        assertThat(badToken.status()).isEqualTo(401);
        assertThat(badToken.code()).isEqualTo(401);
    }

    @Test
    @DisplayName("已认证但无权限返回 HTTP 403 + 统一 JSON")
    void forbiddenReturnsUnified403() throws Exception {
        var r = perform(get("/api/products/admin-list"), login("user1", "123456"), null);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo(403);
        assertThat(r.msg()).isEqualTo("无权限访问");
    }
}
