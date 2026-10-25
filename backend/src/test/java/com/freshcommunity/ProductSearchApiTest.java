package com.freshcommunity;

import com.freshcommunity.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 商品检索（product-catalog spec：关键词 / 分类 / 排序 / 分页 / 管理端全量列表）。
 * schema.sql 预置了 3 个商品：红颜草莓（水果 29.90）、烟台红富士（水果 19.90）、新鲜菠菜（蔬菜 4.50）。
 */
@DisplayName("商品检索：关键词/分类/排序/分页与管理端列表")
class ProductSearchApiTest extends ApiTestBase {

    private long insertProduct(String name, String category, String price, int stock, int status, int deleted) {
        jdbcTemplate.update(
                "INSERT INTO product (name, category, price, stock, image_url, description, status, deleted) " +
                        "VALUES (?, ?, ?, ?, '', ?, ?, ?)",
                name, category, new BigDecimal(price), stock, name + "的描述", status, deleted);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("默认分页：size 生效、total 为在售总数、字段含分类")
    void paginationReturnsPageShape() throws Exception {
        String token = login("user1", "123456");
        for (int i = 0; i < 10; i++) {
            insertProduct("测试蔬菜" + i, "蔬菜", "3.00", 50, 1, 0);
        }

        var data = perform(get("/api/products").param("page", "1").param("size", "5"), token, null).data();

        assertThat(data.path("records").size()).isEqualTo(5);
        assertThat(data.path("total").asLong()).isEqualTo(13); // 3 预置 + 10 新增
        assertThat(data.path("current").asLong()).isEqualTo(1);
        assertThat(data.path("size").asLong()).isEqualTo(5);
        assertThat(data.path("records").get(0).has("category")).isTrue();
        assertThat(data.path("records").get(0).has("description")).isTrue();
    }

    @Test
    @DisplayName("关键词按名称模糊过滤，空串等价于不筛")
    void keywordFiltersByName() throws Exception {
        String token = login("user1", "123456");

        var hit = perform(get("/api/products").param("keyword", "草莓"), token, null).data();
        assertThat(hit.path("records").size()).isEqualTo(1);
        assertThat(hit.path("records").get(0).path("name").asText()).isEqualTo("红颜草莓");

        var blank = perform(get("/api/products").param("keyword", "  "), token, null).data();
        assertThat(blank.path("total").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("分类筛选与 categories 接口一致：只出现在售分类，升序去重")
    void categoryFilterAndList() throws Exception {
        String token = login("user1", "123456");
        insertProduct("下架柠檬", "水果", "9.90", 10, 0, 0);

        var fruit = perform(get("/api/products").param("category", "水果"), token, null).data();
        assertThat(fruit.path("total").asLong()).isEqualTo(2); // 下架柠檬不计入

        var cats = perform(get("/api/products/categories"), token, null).data();
        assertThat(cats.toString()).isEqualTo("[\"水果\",\"蔬菜\"]"); // 升序，且无空分类
    }

    @Test
    @DisplayName("排序白名单：价格升/降正确，非法值回落最新上架不报错")
    void sortWhitelist() throws Exception {
        String token = login("user1", "123456");

        var asc = perform(get("/api/products").param("sort", "price_asc"), token, null).data();
        assertThat(asc.path("records").get(0).path("name").asText()).isEqualTo("新鲜菠菜");

        var desc = perform(get("/api/products").param("sort", "price_desc"), token, null).data();
        assertThat(desc.path("records").get(0).path("name").asText()).isEqualTo("红颜草莓");

        var illegal = perform(get("/api/products").param("sort", "; DROP TABLE product"), token, null);
        assertThat(illegal.ok()).isTrue();
        assertThat(illegal.data().path("total").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("逻辑删除的商品不出现在任何列表")
    void deletedProductHidden() throws Exception {
        String token = login("user1", "123456");
        long id = insertProduct("幽灵商品", "水果", "1.00", 1, 1, 1);

        var user = perform(get("/api/products").param("keyword", "幽灵"), token, null).data();
        assertThat(user.path("total").asLong()).isZero();

        var admin = perform(get("/api/products/admin-list").param("keyword", "幽灵"),
                login("admin", "123456"), null).data();
        assertThat(admin.path("total").asLong()).isZero();
        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("管理端全量列表含下架商品并支持状态筛选；普通用户 403")
    void adminListSeesOffShelf() throws Exception {
        String admin = login("admin", "123456");
        insertProduct("下架西瓜", "水果", "29.00", 5, 0, 0);

        var all = perform(get("/api/products/admin-list"), admin, null).data();
        assertThat(all.path("total").asLong()).isEqualTo(4);

        var off = perform(get("/api/products/admin-list").param("status", "0"), admin, null).data();
        assertThat(off.path("total").asLong()).isEqualTo(1);
        assertThat(off.path("records").get(0).path("name").asText()).isEqualTo("下架西瓜");

        assertThat(perform(get("/api/products/admin-list"), login("user1", "123456"), null).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("管理员新增商品不填分类时落默认「其他」")
    void createDefaultsCategory() throws Exception {
        String admin = login("admin", "123456");

        var r = perform(post("/api/products"), admin, java.util.Map.of(
                "name", "无分类土豆", "price", 2.50, "stock", 30));
        assertThat(r.ok()).isTrue();

        String category = jdbcTemplate.queryForObject(
                "SELECT category FROM product WHERE name = '无分类土豆'", String.class);
        assertThat(category).isEqualTo("其他");
    }
}
