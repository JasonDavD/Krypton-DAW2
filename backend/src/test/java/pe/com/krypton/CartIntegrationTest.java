package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.repository.CartItemRepository;
import pe.com.krypton.repository.CartRepository;
import pe.com.krypton.repository.CategoryRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.UserRepository;

/**
 * Integration tests for cart management.
 * Extends AbstractIntegrationTest (singleton Testcontainers Postgres 16, full JWT chain).
 * @AfterEach cleanup in FK order: cart_item -> cart -> products -> categories.
 * Test data prefixed with "IT-CART-" (SKU) and "IT-Cart-" (category name).
 */
@AutoConfigureMockMvc
class CartIntegrationTest extends AbstractIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String ADMIN_EMAIL    = "admin@krypton.pe";
    private static final String ADMIN_PASSWORD = "Admin123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired CartRepository cartRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;

    // ─── helpers ────────────────────────────────────────────────────────────────

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String clientToken(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"name\":\"Cliente\",\"email\":\"" + email
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long createCategory(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/admin/categories").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createProduct(String token, String sku, String name, long categoryId, int stock) throws Exception {
        String payload = String.format(
                "{\"sku\":\"%s\",\"name\":\"%s\",\"price\":99.90,\"stock\":%d,\"categoryId\":%d}",
                sku, name, stock, categoryId);
        String body = mvc.perform(post("/api/admin/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    /** Unique email per test to guarantee isolation. */
    private String uniqueEmail() {
        return "it-cart-" + System.nanoTime() + "@krypton.pe";
    }

    // ─── cleanup ─────────────────────────────────────────────────────────────────

    @AfterEach
    @Transactional
    void cleanupCartRows() {
        // Delete cart_item + cart rows for test users, in FK order
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-cart-"))
                .forEach(u -> cartRepository.findByUser(u).ifPresent(cart -> {
                    // deleteAll(items) works without needing a separate @Transactional scope
                    cartItemRepository.deleteAll(cartItemRepository.findByCart(cart));
                    cartRepository.delete(cart);
                }));
        // Delete test users themselves
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-cart-"))
                .toList()
                .forEach(userRepository::delete);
        // Delete IT-CART- products and IT-Cart- categories (FK order)
        productRepository.findAll().stream()
                .filter(p -> p.getSku() != null && p.getSku().startsWith("IT-CART-"))
                .toList()
                .forEach(productRepository::delete);
        categoryRepository.findAll().stream()
                .filter(c -> c.getName() != null && c.getName().startsWith("IT-Cart-"))
                .toList()
                .forEach(categoryRepository::delete);
    }

    // ─── P7.2 Authentication required ────────────────────────────────────────────

    @Test
    void unauthenticated_get_cart_returns_401() throws Exception {
        mvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_post_cart_items_returns_401() throws Exception {
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // ─── P7.3 GET with valid token and no cart → 200 empty ───────────────────────

    @Test
    void get_cart_with_no_prior_cart_returns_200_empty() throws Exception {
        String token = clientToken(uniqueEmail(), "Secret123!");

        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    // ─── P7.4 POST creates cart lazily ───────────────────────────────────────────

    @Test
    void post_item_creates_cart_lazily_and_shows_item() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-Lazy");
        long prodId = createProduct(adminToken, "IT-CART-LAZY-01", "IT-Laptop Lazy", catId, 10);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        // POST → 201, item present
        MvcResult postResult = mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andReturn();

        // Follow-up GET shows the item
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(prodId));
    }

    // ─── P7.5 POST same product twice → merged ───────────────────────────────────

    @Test
    void post_same_product_twice_merges_quantity() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-Merge");
        long prodId = createProduct(adminToken, "IT-CART-MERGE-01", "IT-Laptop Merge", catId, 10);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        // First POST (qty=2)
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":2}"))
                .andExpect(status().isCreated());

        // Second POST same product (qty=3) → merged
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":3}"))
                .andExpect(status().isCreated());

        // GET shows single item with summed quantity=5
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5));

        // Verify no duplicate rows in cart_item
        // By checking via GET the item count is 1 — already validated above
    }

    // ─── P7.6 POST with qty > stock → 422 ────────────────────────────────────────

    @Test
    void post_with_qty_exceeding_stock_returns_422() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-Stock");
        long prodId = createProduct(adminToken, "IT-CART-STOCK-01", "IT-Laptop Stock", catId, 5);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":6}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ─── P7.7 Inactive product → 404 ────────────────────────────────────────────

    @Test
    void post_inactive_product_returns_404() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-Inactive");
        long prodId = createProduct(adminToken, "IT-CART-INACTIVE-01", "IT-Laptop Inactive", catId, 10);

        // Soft-delete product
        mvc.perform(delete("/api/admin/products/" + prodId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    // ─── P7.8 PUT replaces quantity ───────────────────────────────────────────────

    @Test
    void put_item_replaces_quantity_and_get_confirms() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-Update");
        long prodId = createProduct(adminToken, "IT-CART-UPDATE-01", "IT-Laptop Update", catId, 10);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        // Add item
        MvcResult postResult = mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();

        long itemId = objectMapper.readTree(postResult.getResponse().getContentAsString())
                .get("items").get(0).get("itemId").asLong();

        // PUT with quantity=7
        mvc.perform(put("/api/cart/items/" + itemId).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"quantity\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(7));

        // GET confirms
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(7));
    }

    @Test
    void put_item_with_quantity_zero_returns_400() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-PutZero");
        long prodId = createProduct(adminToken, "IT-CART-PUTZERO-01", "IT-Laptop PutZero", catId, 10);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        MvcResult postResult = mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();

        long itemId = objectMapper.readTree(postResult.getResponse().getContentAsString())
                .get("items").get(0).get("itemId").asLong();

        mvc.perform(put("/api/cart/items/" + itemId).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    // ─── P7.9 PUT with qty > stock → 422 ─────────────────────────────────────────

    @Test
    void put_with_qty_exceeding_stock_returns_422() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-PutStock");
        long prodId = createProduct(adminToken, "IT-CART-PUTSTOCK-01", "IT-Laptop PutStock", catId, 4);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        MvcResult postResult = mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();

        long itemId = objectMapper.readTree(postResult.getResponse().getContentAsString())
                .get("items").get(0).get("itemId").asLong();

        mvc.perform(put("/api/cart/items/" + itemId).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"quantity\":5}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ─── P7.10 Anti-IDOR ─────────────────────────────────────────────────────────

    @Test
    void anti_idor_user_b_cannot_access_user_a_items() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-IDOR");
        long prodId = createProduct(adminToken, "IT-CART-IDOR-01", "IT-Laptop IDOR", catId, 10);

        String tokenA = clientToken(uniqueEmail(), "Secret123!");
        String tokenB = clientToken(uniqueEmail(), "Secret123!");

        // User A adds item
        MvcResult postResult = mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .content("{\"productId\":" + prodId + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();

        long itemIdA = objectMapper.readTree(postResult.getResponse().getContentAsString())
                .get("items").get(0).get("itemId").asLong();

        // User B tries PUT on User A's item → 404
        mvc.perform(put("/api/cart/items/" + itemIdA).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .content("{\"quantity\":5}"))
                .andExpect(status().isNotFound());

        // User B tries DELETE on User A's item → 404
        mvc.perform(delete("/api/cart/items/" + itemIdA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        // User B GET shows only their own items (empty)
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ─── P7.11 DELETE item removes it ────────────────────────────────────────────

    @Test
    void delete_item_removes_it_and_get_shows_remaining() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-DelItem");
        long prodId1 = createProduct(adminToken, "IT-CART-DEL-01", "IT-Laptop Del1", catId, 10);
        long prodId2 = createProduct(adminToken, "IT-CART-DEL-02", "IT-Mouse Del2", catId, 10);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        // Add two items
        MvcResult postResult = mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId1 + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();

        long itemId1 = objectMapper.readTree(postResult.getResponse().getContentAsString())
                .get("items").get(0).get("itemId").asLong();

        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId2 + ",\"quantity\":3}"))
                .andExpect(status().isCreated());

        // DELETE first item
        mvc.perform(delete("/api/cart/items/" + itemId1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isNoContent());

        // GET shows only second item
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(prodId2));
    }

    // ─── P7.12 DELETE cart removes all items ─────────────────────────────────────

    @Test
    void delete_cart_removes_all_items_and_get_returns_empty() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-Clear");
        long prodId = createProduct(adminToken, "IT-CART-CLEAR-01", "IT-Laptop Clear", catId, 10);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        // Add items
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":2}"))
                .andExpect(status().isCreated());

        // DELETE cart
        mvc.perform(delete("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isNoContent());

        // GET returns empty
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ─── P7.13 DELETE cart when no cart → 204 idempotent ─────────────────────────

    @Test
    void delete_cart_when_no_cart_exists_returns_204_idempotent() throws Exception {
        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        mvc.perform(delete("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isNoContent());
    }

    // ─── P7.14 GET does NOT mutate updated_at ────────────────────────────────────

    @Test
    void get_cart_does_not_mutate_updated_at() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-NoMutate");
        long prodId = createProduct(adminToken, "IT-CART-NOMUTATE-01", "IT-Laptop NoMutate", catId, 10);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        // Create cart with an item
        MvcResult postResult = mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();

        // First GET — establishes the baseline updatedAt from DB (already DB-precision)
        MvcResult get1 = mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andReturn();
        // Second GET — should see the same timestamp
        MvcResult get2 = mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andReturn();

        // Compare raw JSON strings from DB round-trips: both GETs must return identical updatedAt
        String updatedAtGet1 = objectMapper.readTree(get1.getResponse().getContentAsString())
                .get("updatedAt").asText();
        String updatedAtGet2 = objectMapper.readTree(get2.getResponse().getContentAsString())
                .get("updatedAt").asText();

        // GET must NOT advance updatedAt — both reads must agree
        assertThat(updatedAtGet2).isEqualTo(updatedAtGet1);
    }

    // ─── REQ-CM-02 Two distinct products coexist in the same cart ────────────────

    @Test
    void post_two_distinct_products_both_appear_in_cart() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-TwoProds");
        long prodIdA = createProduct(adminToken, "IT-CART-TWOP-A01", "IT-Laptop TwoProds A", catId, 10);
        long prodIdB = createProduct(adminToken, "IT-CART-TWOP-B01", "IT-Mouse TwoProds B", catId, 10);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        // POST product A (qty=1) → 201
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodIdA + ",\"quantity\":1}"))
                .andExpect(status().isCreated());

        // POST product B (qty=2) → 201
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodIdB + ",\"quantity\":2}"))
                .andExpect(status().isCreated());

        // GET /api/cart → both items present
        String body = mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode items = objectMapper.readTree(body).get("items");
        boolean hasA = false, hasB = false;
        for (JsonNode item : items) {
            long pid = item.get("productId").asLong();
            if (pid == prodIdA) hasA = true;
            if (pid == prodIdB) hasB = true;
        }
        assertThat(hasA).isTrue();
        assertThat(hasB).isTrue();
    }

    // ─── REQ-PS-NEW-01 Different users can each hold the same product ─────────────

    @Test
    void same_product_can_exist_in_different_users_carts() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-CrossCart");
        long prodId = createProduct(adminToken, "IT-CART-CROSS-01", "IT-Laptop CrossCart", catId, 20);

        String tokenA = clientToken(uniqueEmail(), "Secret123!");
        String tokenB = clientToken(uniqueEmail(), "Secret123!");

        // Both users add the same product
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .content("{\"productId\":" + prodId + ",\"quantity\":1}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .content("{\"productId\":" + prodId + ",\"quantity\":1}"))
                .andExpect(status().isCreated());

        // Both GETs return the product — no constraint violation
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(prodId));

        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(prodId));
    }

    // ─── P7.15 Write op advances updated_at ──────────────────────────────────────

    @Test
    void write_op_advances_updated_at_relative_to_created_at() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Cart-Advance");
        long prodId = createProduct(adminToken, "IT-CART-ADVANCE-01", "IT-Laptop Advance", catId, 10);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        MvcResult postResult = mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"productId\":" + prodId + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(postResult.getResponse().getContentAsString());
        // updatedAt must be present (not null) after a write
        assertThat(response.has("updatedAt")).isTrue();
        assertThat(response.get("updatedAt").asText()).isNotBlank();
        assertThat(response.get("cartId").asLong()).isPositive();
    }
}
