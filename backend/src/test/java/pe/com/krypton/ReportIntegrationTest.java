package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import pe.com.krypton.repository.CartItemRepository;
import pe.com.krypton.repository.CartRepository;
import pe.com.krypton.repository.CategoryRepository;
import pe.com.krypton.repository.OrderItemRepository;
import pe.com.krypton.repository.OrderRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.StockMovementRepository;
import pe.com.krypton.repository.UserRepository;

/**
 * E2E integration tests for AdminReportController.
 * Real admin JWT round-trip against a full Spring Boot context + Testcontainers Postgres.
 * Extends AbstractIntegrationTest (singleton container, shared across all IT classes).
 *
 * Data prefix: IT-RPT- (products), it-rpt- (users).
 * FK cleanup in @AfterEach follows OrderIntegrationTest pattern:
 *   stock_movement → order_items → orders → cart → users → products → categories.
 *
 * Satisfies REQ-RPT-05 (binary response), REQ-RPT-06 (ADMIN-gated, CLIENTE 403, anon 401).
 */
@AutoConfigureMockMvc
class ReportIntegrationTest extends AbstractIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String ADMIN_EMAIL    = "admin@krypton.pe";
    private static final String ADMIN_PASSWORD = "Admin123!";

    // XLSX magic: PK\x03\x04 (ZIP local file header)
    private static final byte[] XLSX_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04};
    // PDF magic: %PDF
    private static final byte[] PDF_MAGIC  = new byte[]{0x25, 0x50, 0x44, 0x46};

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

    private String uniqueEmail() {
        return "it-rpt-" + System.nanoTime() + "@krypton.pe";
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    // ─── cleanup ─────────────────────────────────────────────────────────────────

    @AfterEach
    void cleanupItRptRows() {
        // IT-RPT- product IDs (for stock_movement filter)
        List<Long> itProductIds = productRepository.findAll().stream()
                .filter(p -> p.getSku() != null && p.getSku().startsWith("IT-RPT-"))
                .map(p -> p.getId())
                .toList();

        // 1. stock_movement rows linked to IT-RPT products
        if (!itProductIds.isEmpty()) {
            stockMovementRepository.findAll().stream()
                    .filter(sm -> sm.getProduct() != null
                            && itProductIds.contains(sm.getProduct().getId()))
                    .toList()
                    .forEach(stockMovementRepository::delete);
        }

        // 2. IT-RPT user IDs
        List<Long> itUserIds = userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-rpt-"))
                .map(u -> u.getId())
                .toList();

        // 3. order_items → orders for IT-RPT users
        orderRepository.findAll().stream()
                .filter(o -> itUserIds.contains(o.getUser().getId()))
                .toList()
                .forEach(o -> {
                    orderItemRepository.deleteAll(orderItemRepository.findByOrder(o));
                    orderRepository.delete(o);
                });

        // 4. cart_item → cart for IT-RPT users
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-rpt-"))
                .forEach(u -> cartRepository.findByUser(u).ifPresent(cart -> {
                    cartItemRepository.deleteAll(cartItemRepository.findByCart(cart));
                    cartRepository.delete(cart);
                }));

        // 5. delete IT-RPT users
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("it-rpt-"))
                .toList()
                .forEach(userRepository::delete);

        // 6. delete IT-RPT products
        productRepository.findAll().stream()
                .filter(p -> p.getSku() != null && p.getSku().startsWith("IT-RPT-"))
                .toList()
                .forEach(productRepository::delete);

        // 7. delete IT-RPT categories
        categoryRepository.findAll().stream()
                .filter(c -> c.getName() != null && c.getName().startsWith("IT-Rpt-"))
                .toList()
                .forEach(categoryRepository::delete);
    }

    // ─── R1 Ventas Excel — admin JWT → 200 XLSX ──────────────────────────────────

    @Test
    void admin_ventas_excel_returns_200_valid_xlsx_binary() throws Exception {
        String adminToken = adminToken();

        MvcResult result = mvc.perform(get("/api/admin/reports/ventas/excel")
                        .param("desde", "2024-01-01")
                        .param("hasta", "2024-12-31")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertThat(contentType).contains("spreadsheetml");

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertThat(startsWith(body, XLSX_MAGIC)).isTrue();

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).contains("attachment");
        assertThat(disposition).contains(".xlsx");
    }

    // ─── R1 Ventas PDF — admin JWT → 200 PDF ─────────────────────────────────────

    @Test
    void admin_ventas_pdf_returns_200_valid_pdf_binary() throws Exception {
        String adminToken = adminToken();

        MvcResult result = mvc.perform(get("/api/admin/reports/ventas/pdf")
                        .param("desde", "2024-01-01")
                        .param("hasta", "2024-12-31")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertThat(contentType).contains("pdf");

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertThat(startsWith(body, PDF_MAGIC)).isTrue();

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).contains("attachment");
        assertThat(disposition).contains(".pdf");
    }

    // ─── CLIENTE role → 403 ──────────────────────────────────────────────────────

    @Test
    void cliente_on_ventas_excel_returns_403() throws Exception {
        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        mvc.perform(get("/api/admin/reports/ventas/excel")
                        .param("desde", "2024-01-01")
                        .param("hasta", "2024-12-31")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cliente_on_ordenes_pdf_returns_403() throws Exception {
        String clientToken = clientToken(uniqueEmail(), "Secret123!");

        mvc.perform(get("/api/admin/reports/ordenes/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isForbidden());
    }

    // ─── No token → 401 ──────────────────────────────────────────────────────────

    @Test
    void unauthenticated_ventas_excel_returns_401() throws Exception {
        mvc.perform(get("/api/admin/reports/ventas/excel")
                        .param("desde", "2024-01-01")
                        .param("hasta", "2024-12-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_ordenes_pdf_returns_401() throws Exception {
        mvc.perform(get("/api/admin/reports/ordenes/pdf"))
                .andExpect(status().isUnauthorized());
    }
}
