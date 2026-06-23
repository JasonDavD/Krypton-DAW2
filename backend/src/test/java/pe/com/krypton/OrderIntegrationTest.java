package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.com.krypton.repository.CartItemRepository;
import pe.com.krypton.repository.CartRepository;
import pe.com.krypton.repository.CategoryRepository;
import pe.com.krypton.repository.OrderItemRepository;
import pe.com.krypton.repository.OrderRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.StockMovementRepository;
import pe.com.krypton.repository.UserRepository;

/**
 * Integration tests for Order management (checkout, pay, admin).
 * Extends AbstractIntegrationTest (singleton Testcontainers Postgres 16, full JWT chain).
 *
 * FK cleanup order in @AfterEach: stock_movement → order_items → orders → cart_item → cart
 * → users (prefix it-ord-) → products (prefix IT-ORD-) → categories (prefix IT-Ord-).
 * Test user email prefix: "it-ord-".
 * Satisfies REQ-OM-01..REQ-OM-13.
 */
@AutoConfigureMockMvc
class OrderIntegrationTest extends AbstractIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    /** Comprobante por defecto para el checkout (ahora @RequestBody obligatorio). */
    private static final String CHECKOUT_BODY =
            "{\"documentType\":\"BOLETA\",\"customerName\":\"Juan Cliente\",\"customerDoc\":\"12345678\"}";
    private static final String ADMIN_EMAIL    = "admin@krypton.pe";
    private static final String ADMIN_PASSWORD = "Admin123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired StockMovementRepository stockMovementRepository;
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

    private long createCategory(String adminToken, String name) throws Exception {
        String body = mvc.perform(post("/api/admin/categories").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createProduct(String adminToken, String sku, String name, long categoryId,
                               int stock, double price) throws Exception {
        String payload = String.format(
                "{\"sku\":\"%s\",\"name\":\"%s\",\"price\":%.2f,\"stock\":%d,\"categoryId\":%d}",
                sku, name, price, stock, categoryId);
        String body = mvc.perform(post("/api/admin/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void addToCart(String token, long productId, int qty) throws Exception {
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content("{\"productId\":" + productId + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated());
    }

    private String uniqueEmail() {
        return "it-ord-" + System.nanoTime() + "@krypton.pe";
    }

    // ─── cleanup ─────────────────────────────────────────────────────────────────

    @AfterEach
    void cleanupOrderRows() {
        // Find IT products first (needed for stock_movement filter, loaded eagerly via id-only check)
        List<Long> itProductIds = productRepository.findAll().stream()
                .filter(p -> p.getSku() != null && p.getSku().startsWith("IT-ORD-"))
                .map(p -> p.getId())
                .toList();

        // 1. stock_movement rows linked to IT products
        if (!itProductIds.isEmpty()) {
            stockMovementRepository.findAll().stream()
                    .filter(sm -> {
                        try {
                            return sm.getProduct() != null
                                    && itProductIds.contains(sm.getProduct().getId());
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList()
                    .forEach(stockMovementRepository::delete);
        }

        // 2. Find IT users
        List<Long> itUserIds = userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-ord-"))
                .map(u -> u.getId())
                .toList();

        // 3. order_items → orders for IT users
        orderRepository.findAll().stream()
                .filter(o -> itUserIds.contains(o.getUser().getId()))
                .toList()
                .forEach(o -> {
                    orderItemRepository.deleteAll(orderItemRepository.findByOrder(o));
                    orderRepository.delete(o);
                });

        // 4. cart_item → cart for IT users
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-ord-"))
                .forEach(u -> cartRepository.findByUser(u).ifPresent(cart -> {
                    cartItemRepository.deleteAll(cartItemRepository.findByCart(cart));
                    cartRepository.delete(cart);
                }));

        // 5. delete IT users
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-ord-"))
                .toList()
                .forEach(userRepository::delete);

        // 6. delete IT products (prefix IT-ORD-)
        productRepository.findAll().stream()
                .filter(p -> p.getSku() != null && p.getSku().startsWith("IT-ORD-"))
                .toList()
                .forEach(productRepository::delete);

        // 7. delete IT categories (prefix IT-Ord-)
        categoryRepository.findAll().stream()
                .filter(c -> c.getName() != null && c.getName().startsWith("IT-Ord-"))
                .toList()
                .forEach(categoryRepository::delete);
    }

    // ─── P6.3 Full checkout flow ─────────────────────────────────────────────────

    @Test
    void full_checkout_creates_order_items_decrements_stock_saves_movement_clears_cart()
            throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-Checkout");
        long prodId = createProduct(adminToken, "IT-ORD-CHK-01", "IT-Notebook Checkout",
                catId, 10, 299.90);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");
        addToCart(clientToken, prodId, 2);

        MvcResult result = mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].unitPrice").value(299.90))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                // Comprobante + desglose: subtotal 599.80 ≥ 300 → envío gratis; IGV desglosado
                .andExpect(jsonPath("$.documentType").value("BOLETA"))
                .andExpect(jsonPath("$.subtotal").value(599.80))
                .andExpect(jsonPath("$.shippingCost").value(0))
                .andExpect(jsonPath("$.igv").value(91.49))
                .andExpect(jsonPath("$.total").value(599.80))
                .andReturn();

        long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        // Assert one orders row with PENDIENTE
        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(orderRepository.findById(orderId).get().getStatus().name()).isEqualTo("PENDIENTE");

        // Assert order_items has one row with unit_price snapshot
        assertThat(orderItemRepository.findAll()).hasSize(1);
        assertThat(orderItemRepository.findAll().get(0).getUnitPrice())
                .isEqualByComparingTo("299.90");

        // Assert product stock decremented: 10 - 2 = 8
        assertThat(productRepository.findById(prodId).get().getStock()).isEqualTo(8);

        // Assert one stock_movement SALIDA row with correct reference
        var movements = stockMovementRepository.findAll();
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getType().name()).isEqualTo("SALIDA");
        assertThat(movements.get(0).getReference()).isEqualTo("ORDER-" + orderId);

        // Assert cart is empty
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ─── P6.4 Checkout rollback on insufficient stock ────────────────────────────

    @Test
    void checkout_rollback_no_order_created_when_stock_exceeded() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-Rollback");
        // Create product with stock=5 so addToCart succeeds
        long prodId = createProduct(adminToken, "IT-ORD-ROLL-01", "IT-Notebook Rollback",
                catId, 5, 99.90);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");
        addToCart(clientToken, prodId, 5); // add max allowed

        // Now reduce product stock to 0 directly in DB to simulate race condition
        productRepository.findById(prodId).ifPresent(p -> {
            p.setStock(0);
            productRepository.save(p);
        });

        // Checkout should fail: qty=5 in cart but stock=0 → 422
        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isUnprocessableEntity());

        // Assert zero orders rows (full rollback)
        List<Long> itUserIds = userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-ord-"))
                .map(u -> u.getId()).toList();
        long ordersForItUsers = orderRepository.findAll().stream()
                .filter(o -> itUserIds.contains(o.getUser().getId())).count();
        assertThat(ordersForItUsers).isEqualTo(0);

        // Assert product stock still 0 (no accidental change)
        assertThat(productRepository.findById(prodId).get().getStock()).isEqualTo(0);
    }

    // ─── P6.5 Empty cart checkout → 400 ─────────────────────────────────────────

    @Test
    void checkout_with_empty_cart_returns_400_no_order_created() throws Exception {
        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isBadRequest());

        // No orders for IT users
        List<Long> itUserIds = userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-ord-"))
                .map(u -> u.getId()).toList();
        long count = orderRepository.findAll().stream()
                .filter(o -> itUserIds.contains(o.getUser().getId())).count();
        assertThat(count).isEqualTo(0);
    }

    // ─── P6.6 Client order history ───────────────────────────────────────────────

    @Test
    void client_order_history_returns_own_orders_newest_first() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-History");
        long prodId1 = createProduct(adminToken, "IT-ORD-HIST-01", "IT-Prod Hist1", catId, 10, 10.00);
        long prodId2 = createProduct(adminToken, "IT-ORD-HIST-02", "IT-Prod Hist2", catId, 10, 20.00);

        String clientTokenA = clientToken(uniqueEmail(), "Secret123!");
        String clientTokenB = clientToken(uniqueEmail(), "Secret123!");

        // Client A places two orders
        addToCart(clientTokenA, prodId1, 1);
        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY).header(HttpHeaders.AUTHORIZATION, bearer(clientTokenA)))
                .andExpect(status().isCreated());

        addToCart(clientTokenA, prodId2, 1);
        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY).header(HttpHeaders.AUTHORIZATION, bearer(clientTokenA)))
                .andExpect(status().isCreated());

        // Client B places one order
        addToCart(clientTokenB, prodId1, 1);
        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY).header(HttpHeaders.AUTHORIZATION, bearer(clientTokenB)))
                .andExpect(status().isCreated());

        // Client A GET → should see exactly 2 orders, not Client B's
        String body = mvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientTokenA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode orders = objectMapper.readTree(body);
        assertThat(orders.size()).isEqualTo(2);
    }

    // ─── P6.7 Client order detail ────────────────────────────────────────────────

    @Test
    void client_order_detail_returns_correct_items_and_userId() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-Detail");
        long prodId = createProduct(adminToken, "IT-ORD-DET-01", "IT-Prod Detail", catId, 10, 50.00);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");
        addToCart(clientToken, prodId, 2);

        MvcResult checkoutResult = mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("id").asLong();
        long userId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("userId").asLong();

        mvc.perform(get("/api/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    // ─── P6.8 IDOR on GET ────────────────────────────────────────────────────────

    @Test
    void IDOR_on_get_order_returns_404_not_403() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-IDOR-G");
        long prodId = createProduct(adminToken, "IT-ORD-IDORG-01", "IT-Prod IDOR Get", catId, 10, 10.00);

        String tokenA = clientToken(uniqueEmail(), "Secret123!");
        String tokenB = clientToken(uniqueEmail(), "Secret123!");

        addToCart(tokenA, prodId, 1);
        MvcResult checkoutResult = mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderIdA = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Client B tries to access Client A's order → 404
        mvc.perform(get("/api/orders/" + orderIdA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
    }

    // ─── P6.9 IDOR on pay ────────────────────────────────────────────────────────

    @Test
    void IDOR_on_pay_returns_404() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-IDOR-P");
        long prodId = createProduct(adminToken, "IT-ORD-IDORP-01", "IT-Prod IDOR Pay", catId, 10, 10.00);

        String tokenA = clientToken(uniqueEmail(), "Secret123!");
        String tokenB = clientToken(uniqueEmail(), "Secret123!");

        addToCart(tokenA, prodId, 1);
        MvcResult checkoutResult = mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderIdA = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Client B tries to pay Client A's order → 404
        mvc.perform(post("/api/orders/" + orderIdA + "/pay").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .content("{\"method\":\"YAPE\"}"))
                .andExpect(status().isNotFound());
    }

    // ─── P6.10 Pay PENDIENTE order ───────────────────────────────────────────────

    @Test
    void pay_pendiente_order_transitions_to_confirmada() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-Pay");
        long prodId = createProduct(adminToken, "IT-ORD-PAY-01", "IT-Prod Pay", catId, 10, 10.00);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");
        addToCart(clientToken, prodId, 1);

        MvcResult checkoutResult = mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("id").asLong();

        mvc.perform(post("/api/orders/" + orderId + "/pay").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"method\":\"YAPE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));

        // Assert DB status
        assertThat(orderRepository.findById(orderId).get().getStatus().name())
                .isEqualTo("CONFIRMADA");
    }

    // ─── P6.11 Pay CONFIRMADA order → 422 ───────────────────────────────────────

    @Test
    void pay_confirmada_order_returns_422_status_unchanged() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-DouPay");
        long prodId = createProduct(adminToken, "IT-ORD-DPAY-01", "IT-Prod DoublePay", catId, 10, 10.00);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");
        addToCart(clientToken, prodId, 1);

        MvcResult checkoutResult = mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("id").asLong();

        // First payment → CONFIRMADA
        mvc.perform(post("/api/orders/" + orderId + "/pay").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"method\":\"YAPE\"}"))
                .andExpect(status().isOk());

        // Second payment → 422
        mvc.perform(post("/api/orders/" + orderId + "/pay").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .content("{\"method\":\"YAPE\"}"))
                .andExpect(status().isUnprocessableEntity());

        // Status remains CONFIRMADA
        assertThat(orderRepository.findById(orderId).get().getStatus().name())
                .isEqualTo("CONFIRMADA");
    }

    // ─── P6.12 Admin paginated list ──────────────────────────────────────────────

    @Test
    void admin_paginated_list_shows_all_orders_with_userId() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-AdminList");
        long prodId = createProduct(adminToken, "IT-ORD-ADM-01", "IT-Prod Admin", catId, 20, 10.00);

        String tokenA = clientToken(uniqueEmail(), "Secret123!");
        String tokenB = clientToken(uniqueEmail(), "Secret123!");

        addToCart(tokenA, prodId, 1);
        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isCreated());

        addToCart(tokenB, prodId, 1);
        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isCreated());

        String body = mvc.perform(get("/api/admin/orders").param("page", "0").param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode page = objectMapper.readTree(body);
        // At least 2 orders exist (might have more from other tests that ran before)
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);

        // All order responses include userId
        for (JsonNode order : page.get("content")) {
            assertThat(order.has("userId")).isTrue();
            assertThat(order.get("userId").asLong()).isPositive();
        }
    }

    // ─── P6.13 Admin order detail ────────────────────────────────────────────────

    @Test
    void admin_can_get_any_users_order() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-AdminDet");
        long prodId = createProduct(adminToken, "IT-ORD-ADET-01", "IT-Prod Admin Det", catId, 10, 10.00);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");
        addToCart(clientToken, prodId, 1);

        MvcResult checkoutResult = mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Admin can get any user's order
        mvc.perform(get("/api/admin/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
    }

    // ─── P6.14 Admin status update ───────────────────────────────────────────────

    @Test
    void admin_can_update_status_with_no_transition_guard() throws Exception {
        String adminToken = adminToken();
        long catId = createCategory(adminToken, "IT-Ord-AdminStatus");
        long prodId = createProduct(adminToken, "IT-ORD-ASTAT-01", "IT-Prod Admin Stat", catId, 10, 10.00);

        String clientToken = clientToken(uniqueEmail(), "Secret123!");
        addToCart(clientToken, prodId, 1);

        MvcResult checkoutResult = mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Admin directly sets CANCELADA (from PENDIENTE — no intermediate step required)
        mvc.perform(put("/api/admin/orders/" + orderId + "/status").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content("{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));

        assertThat(orderRepository.findById(orderId).get().getStatus().name())
                .isEqualTo("CANCELADA");
    }

    // ─── P6.15 401 unauthenticated ───────────────────────────────────────────────

    @Test
    void unauthenticated_checkout_returns_401() throws Exception {
        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_admin_list_returns_401() throws Exception {
        mvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    // ─── P6.16 403 CLIENTE on admin endpoint ────────────────────────────────────

    @Test
    void cliente_on_admin_endpoint_returns_403() throws Exception {
        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        mvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isForbidden());
    }
}
