package pe.com.krypton.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import pe.com.krypton.dto.response.CartItemResponse;
import pe.com.krypton.dto.response.CartResponse;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.CartService;

/**
 * Web slice para CartController. SecurityContext desactivado vía addFilters=false +
 * exclusión de JwtAuthenticationFilter. Principal inyectado con @WithMockUser.
 * Cubre solo el contrato HTTP: status codes, validación, mapeo de excepciones.
 */
@WebMvcTest(controllers = CartController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CartService cartService;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String USER_EMAIL = "user@krypton.pe";

    private CartResponse sampleCart() {
        CartItemResponse item = new CartItemResponse(
                5L, 12L, "Laptop", "LAP-001",
                new BigDecimal("999.90"), 2, new BigDecimal("1999.80"));
        return new CartResponse(1L, List.of(item), new BigDecimal("1999.80"), Instant.now());
    }

    private CartResponse emptyCart() {
        return new CartResponse(null, List.of(), BigDecimal.ZERO, null);
    }

    // ─── GET /api/cart ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void get_cart_returns_200_with_items_and_total() throws Exception {
        when(cartService.getCart(USER_EMAIL)).thenReturn(sampleCart());

        mvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(1999.80));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void get_cart_returns_200_empty_when_no_cart() throws Exception {
        when(cartService.getCart(USER_EMAIL)).thenReturn(emptyCart());

        mvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.cartId").doesNotExist());
    }

    // ─── POST /api/cart/items ────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void post_item_returns_201_with_cart_response() throws Exception {
        when(cartService.addItem(eq(USER_EMAIL), any())).thenReturn(sampleCart());

        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .content("{\"productId\":12,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void post_item_with_quantity_zero_returns_400() throws Exception {
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .content("{\"productId\":12,\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).addItem(any(), any());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void post_item_with_negative_quantity_returns_400() throws Exception {
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .content("{\"productId\":1,\"quantity\":-5}"))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).addItem(any(), any());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void post_item_missing_productId_returns_400() throws Exception {
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).addItem(any(), any());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void post_item_missing_quantity_returns_400() throws Exception {
        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .content("{\"productId\":12}"))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).addItem(any(), any());
    }

    // ─── PUT /api/cart/items/{itemId} ────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void put_item_returns_200_with_cart_response() throws Exception {
        when(cartService.updateItem(eq(USER_EMAIL), eq(5L), any())).thenReturn(sampleCart());

        mvc.perform(put("/api/cart/items/5").contentType(JSON)
                        .content("{\"quantity\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void put_item_with_quantity_zero_returns_400() throws Exception {
        mvc.perform(put("/api/cart/items/5").contentType(JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).updateItem(any(), any(), any());
    }

    // ─── DELETE /api/cart/items/{itemId} ─────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void delete_item_returns_204() throws Exception {
        doNothing().when(cartService).removeItem(USER_EMAIL, 5L);

        mvc.perform(delete("/api/cart/items/5"))
                .andExpect(status().isNoContent());
    }

    // ─── DELETE /api/cart ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void delete_cart_returns_204() throws Exception {
        doNothing().when(cartService).clearCart(USER_EMAIL);

        mvc.perform(delete("/api/cart"))
                .andExpect(status().isNoContent());
    }

    // ─── Exception mapping ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL)
    void insufficient_stock_exception_returns_422() throws Exception {
        when(cartService.addItem(eq(USER_EMAIL), any()))
                .thenThrow(new InsufficientStockException("Stock insuficiente"));

        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .content("{\"productId\":12,\"quantity\":99}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void resource_not_found_exception_returns_404() throws Exception {
        when(cartService.addItem(eq(USER_EMAIL), any()))
                .thenThrow(new ResourceNotFoundException("Producto no encontrado"));

        mvc.perform(post("/api/cart/items").contentType(JSON)
                        .content("{\"productId\":99,\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }
}
