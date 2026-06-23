package pe.com.krypton.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.dto.response.ProductResponse;
import pe.com.krypton.exception.DuplicateSkuException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.ProductService;

/**
 * Web slice del AdminProductController (service mockeado, seguridad desactivada).
 * El borde de seguridad (401 sin token, 403 no-ADMIN) se prueba en integración (Phase 5).
 */
@WebMvcTest(controllers = AdminProductController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AdminProductControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ProductService productService;

    private static final String VALID_BODY = """
            {"sku":"SKU-01","name":"Laptop Pro","description":"Desc","price":1500.00,"stock":5,"imageUrl":null,"categoryId":1}
            """;

    private ProductResponse sampleProduct(Long id) {
        return new ProductResponse(id, "SKU-01", "Laptop Pro", "Desc",
                new BigDecimal("1500.00"), 5, null, true, 1L, "Electronics", null);
    }

    @Test
    void should_return_201_when_create_is_valid() throws Exception {
        when(productService.create(any())).thenReturn(sampleProduct(10L));

        mvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.sku").value("SKU-01"));
    }

    @Test
    void should_return_409_when_create_has_duplicate_sku() throws Exception {
        when(productService.create(any())).thenThrow(new DuplicateSkuException("SKU ya registrado"));

        mvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void should_return_400_when_create_body_is_invalid() throws Exception {
        // missing required fields: sku, name, price, stock, categoryId
        mvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"only desc\"}"))
                .andExpect(status().isBadRequest());

        verify(productService, never()).create(any());
    }

    @Test
    void should_return_200_when_update_is_valid() throws Exception {
        when(productService.update(eq(10L), any())).thenReturn(sampleProduct(10L));

        mvc.perform(put("/api/admin/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void should_return_404_when_update_product_not_found() throws Exception {
        when(productService.update(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Producto no encontrado"));

        mvc.perform(put("/api/admin/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_409_when_update_has_duplicate_sku() throws Exception {
        when(productService.update(eq(10L), any()))
                .thenThrow(new DuplicateSkuException("SKU ya usado por otro producto"));

        mvc.perform(put("/api/admin/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void should_return_204_when_delete_succeeds() throws Exception {
        doNothing().when(productService).delete(10L);

        mvc.perform(delete("/api/admin/products/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_return_404_when_delete_product_not_found() throws Exception {
        doThrow(new ResourceNotFoundException("Producto no encontrado"))
                .when(productService).delete(99L);

        mvc.perform(delete("/api/admin/products/99"))
                .andExpect(status().isNotFound());
    }
}
