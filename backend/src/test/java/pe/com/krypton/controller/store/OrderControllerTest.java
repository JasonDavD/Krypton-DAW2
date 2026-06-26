package pe.com.krypton.controller.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.dto.response.ItemOrdenResponse;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.exception.ComprobanteNotAvailableException;
import pe.com.krypton.exception.EmptyCartException;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.InvalidDocumentException;
import pe.com.krypton.exception.OrderStatusTransitionException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.OrdenService;

/**
 * Web slice for OrdenController.
 * SecurityContext disabled via addFilters=false + JwtAuthenticationFilter exclusion.
 * Principal injected with @WithMockUser. OrdenService is mocked.
 * Covers HTTP contract: status codes, JSON shape, validation, exception mapping.
 * Satisfies REQ-OM-01..REQ-OM-09, REQ-OM-13.
 */
@WebMvcTest(controllers = OrdenController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @MockBean OrdenService orderService;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String USER_EMAIL = "client@krypton.pe";
    private static final String CHECKOUT_BODY =
            "{\"documentType\":\"BOLETA\",\"customerName\":\"Juan Cliente\",\"customerDoc\":\"12345678\"}";

    private OrdenResponse sampleOrder(Long id, String status) {
        ItemOrdenResponse item = new ItemOrdenResponse(
                1L, 12L, "Notebook", 2, new BigDecimal("2999.90"), new BigDecimal("5999.80"));
        return new OrdenResponse(id, 3L, Instant.now(), status,
                "BOLETA", "Juan Cliente", "12345678",
                new BigDecimal("5999.80"), BigDecimal.ZERO, new BigDecimal("915.22"),
                new BigDecimal("5999.80"), List.of(item));
    }

    // ─── POST /api/orders/checkout ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void checkout_returns_201_with_order_response() throws Exception {
        when(orderService.confirmarCompra(eq(USER_EMAIL), any())).thenReturn(sampleOrder(1L, "PENDIENTE"));

        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.userId").value(3))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void checkout_empty_cart_returns_400() throws Exception {
        when(orderService.confirmarCompra(eq(USER_EMAIL), any()))
                .thenThrow(new EmptyCartException("El carrito está vacío"));

        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void checkout_insufficient_stock_returns_422() throws Exception {
        when(orderService.confirmarCompra(eq(USER_EMAIL), any()))
                .thenThrow(new InsufficientStockException("Stock insuficiente"));

        mvc.perform(post("/api/orders/checkout").contentType(JSON).content(CHECKOUT_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void checkout_missing_document_type_returns_400() throws Exception {
        // documentType es @NotNull → bean validation rechaza antes de tocar el service
        mvc.perform(post("/api/orders/checkout").contentType(JSON)
                        .content("{\"customerName\":\"Juan\",\"customerDoc\":\"12345678\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void checkout_invalid_document_for_type_returns_422() throws Exception {
        // FACTURA con DNI (8 díg) pasa el @Pattern genérico pero el service la rechaza → 422
        when(orderService.confirmarCompra(eq(USER_EMAIL), any()))
                .thenThrow(new InvalidDocumentException("La factura requiere un RUC de 11 dígitos"));

        mvc.perform(post("/api/orders/checkout").contentType(JSON)
                        .content("{\"documentType\":\"FACTURA\",\"customerName\":\"ACME\",\"customerDoc\":\"12345678\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ─── GET /api/orders ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void get_my_orders_returns_200_with_list() throws Exception {
        when(orderService.misOrdenes(USER_EMAIL))
                .thenReturn(List.of(sampleOrder(1L, "PENDIENTE"), sampleOrder(2L, "CONFIRMADA")));

        mvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ─── GET /api/orders/{id} ────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void get_my_order_returns_200_with_detail() throws Exception {
        when(orderService.miOrden(USER_EMAIL, 5L)).thenReturn(sampleOrder(5L, "PENDIENTE"));

        mvc.perform(get("/api/orders/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void get_my_order_IDOR_returns_404() throws Exception {
        when(orderService.miOrden(USER_EMAIL, 9L))
                .thenThrow(new ResourceNotFoundException("Orden no encontrada: 9"));

        mvc.perform(get("/api/orders/9"))
                .andExpect(status().isNotFound());
    }

    // ─── POST /api/orders/{id}/pay ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void pay_returns_200_with_confirmada_order() throws Exception {
        when(orderService.pagar(eq(USER_EMAIL), eq(3L), any()))
                .thenReturn(sampleOrder(3L, "CONFIRMADA"));

        mvc.perform(post("/api/orders/3/pay").contentType(JSON)
                        .content("{\"method\":\"YAPE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void pay_wrong_status_returns_422() throws Exception {
        when(orderService.pagar(eq(USER_EMAIL), eq(4L), any()))
                .thenThrow(new OrderStatusTransitionException("Solo se puede pagar PENDIENTE"));

        mvc.perform(post("/api/orders/4/pay").contentType(JSON)
                        .content("{\"method\":\"YAPE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void pay_IDOR_returns_404() throws Exception {
        when(orderService.pagar(eq(USER_EMAIL), eq(8L), any()))
                .thenThrow(new ResourceNotFoundException("Orden no encontrada: 8"));

        mvc.perform(post("/api/orders/8/pay").contentType(JSON)
                        .content("{\"method\":\"CREDIT_CARD\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void pay_missing_method_returns_400() throws Exception {
        mvc.perform(post("/api/orders/3/pay").contentType(JSON)
                        .content("{}")) // method is @NotNull
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/orders/{id}/comprobante ─────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void comprobante_returns_200_pdf() throws Exception {
        when(orderService.miComprobantePdf(eq(USER_EMAIL), eq(5L)))
                .thenReturn(new byte[]{ 0x25, 0x50, 0x44, 0x46 });

        mvc.perform(get("/api/orders/5/comprobante"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void comprobante_unpaid_returns_409() throws Exception {
        when(orderService.miComprobantePdf(eq(USER_EMAIL), eq(6L)))
                .thenThrow(new ComprobanteNotAvailableException("El pedido no está pagado"));

        mvc.perform(get("/api/orders/6/comprobante"))
                .andExpect(status().isConflict());
    }

    // ─── Auth: no token → 401 (checked via integration test; web slice skips JWT) ──
    // Note: @AutoConfigureMockMvc(addFilters=false) means 401 tests belong in IT.
    // The spec calls P5.3 auth cases; we document: unauthenticated → 401 verified in IT.
}
