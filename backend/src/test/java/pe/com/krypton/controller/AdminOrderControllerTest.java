package pe.com.krypton.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import pe.com.krypton.dto.response.OrderResponse;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.exception.OrderStatusTransitionException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.OrdenService;

/**
 * Web slice for AdminOrdenController.
 * SecurityContext disabled via addFilters=false + JwtAuthenticationFilter exclusion.
 * OrdenService mocked. Rol assigned via @WithMockUser(roles = "ADMIN").
 * Covers HTTP contract: status codes, JSON shape, validation.
 * Satisfies REQ-OM-10..REQ-OM-13.
 */
@WebMvcTest(controllers = AdminOrdenController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AdminOrderControllerTest {

    @Autowired MockMvc mvc;
    @MockBean OrdenService orderService;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private OrderResponse sampleOrder(Long id, String status) {
        return new OrderResponse(id, 3L, Instant.now(), status,
                "BOLETA", "Juan Cliente", "12345678",
                new BigDecimal("299.90"), BigDecimal.ZERO, new BigDecimal("45.75"),
                new BigDecimal("299.90"), List.of());
    }

    private PageResponse<OrderResponse> singlePage(OrderResponse order) {
        return new PageResponse<>(List.of(order), 0, 10, 1L, 1);
    }

    // ─── GET /api/admin/orders ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void get_all_orders_returns_200_page_response() throws Exception {
        when(orderService.getAllOrders(any(), any(), any(), any()))
                .thenReturn(singlePage(sampleOrder(1L, "PENDIENTE")));

        mvc.perform(get("/api/admin/orders").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(3));
    }

    // ─── GET /api/admin/orders/{id} ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void get_admin_order_returns_200_with_detail() throws Exception {
        when(orderService.getOrder(10L)).thenReturn(sampleOrder(10L, "PENDIENTE"));

        mvc.perform(get("/api/admin/orders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void get_admin_order_not_found_returns_404() throws Exception {
        when(orderService.getOrder(999L))
                .thenThrow(new ResourceNotFoundException("Orden no encontrada: 999"));

        mvc.perform(get("/api/admin/orders/999"))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/admin/orders/{id}/status ───────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void put_status_returns_200_with_updated_order() throws Exception {
        when(orderService.updateStatus(eq(2L), any()))
                .thenReturn(sampleOrder(2L, "CANCELADA"));

        mvc.perform(put("/api/admin/orders/2/status").contentType(JSON)
                        .content("{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void put_status_illegal_transition_returns_422() throws Exception {
        // Contrato de capa web: una transición ilegal (CANCELADA → CONFIRMADA) que el
        // service rechaza con OrderStatusTransitionException debe salir como 422,
        // gracias al mapeo del GlobalExceptionHandler (@RestControllerAdvice).
        when(orderService.updateStatus(eq(2L), any()))
                .thenThrow(new OrderStatusTransitionException(
                        "Transición de estado inválida: CANCELADA → CONFIRMADA"));

        mvc.perform(put("/api/admin/orders/2/status").contentType(JSON)
                        .content("{\"status\":\"CONFIRMADA\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void put_status_missing_field_returns_400() throws Exception {
        mvc.perform(put("/api/admin/orders/2/status").contentType(JSON)
                        .content("{}")) // status is @NotNull
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/admin/orders/{id}/comprobante ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void comprobante_returns_200_pdf() throws Exception {
        when(orderService.getComprobantePdf(eq(7L)))
                .thenReturn(new byte[]{ 0x25, 0x50, 0x44, 0x46 });

        mvc.perform(get("/api/admin/orders/7/comprobante"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    // ─── Auth notes ──────────────────────────────────────────────────────────────
    // 401 (no token) and 403 (CLIENTE role on admin endpoint) are verified in integration tests
    // because addFilters=false disables the JWT filter in this web slice.
}
