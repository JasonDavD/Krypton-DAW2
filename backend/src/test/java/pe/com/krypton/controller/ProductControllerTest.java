package pe.com.krypton.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.dto.response.ProductImageResponse;
import pe.com.krypton.dto.response.ProductResponse;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.ProductService;

/**
 * Web slice: solo la capa HTTP de ProductController.
 * Seguridad desactivada (addFilters=false) — verificar 401/403 en integración (Phase 5).
 */
@WebMvcTest(controllers = ProductController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ProductService productService;

    private ProductResponse sampleProduct(Long id) {
        return new ProductResponse(id, "SKU-" + id, "Product " + id, "Desc",
                new BigDecimal("99.90"), 10, null, true, 1L, "Electronics", null);
    }

    private ProductResponse sampleProductWithImages(Long id) {
        List<ProductImageResponse> images = List.of(
                new ProductImageResponse(10L, "http://localhost:8080/api/uploads/images/a.jpg", (short) 0, true),
                new ProductImageResponse(11L, "http://localhost:8080/api/uploads/images/b.jpg", (short) 1, false));
        return new ProductResponse(id, "SKU-" + id, "Product " + id, "Desc",
                new BigDecimal("99.90"), 10, "http://localhost:8080/api/uploads/images/a.jpg",
                true, 1L, "Electronics", images);
    }

    @Test
    void should_return_200_and_page_when_listing_products() throws Exception {
        var page = new PageImpl<>(List.of(sampleProduct(1L), sampleProduct(2L)),
                PageRequest.of(0, 20), 2);
        when(productService.search(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(PageResponse.of(page));

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].sku").value("SKU-1"));
    }

    @Test
    void should_return_200_with_filters_when_name_param_provided() throws Exception {
        var page = new PageImpl<>(List.of(sampleProduct(3L)), PageRequest.of(0, 20), 1);
        when(productService.search(any(), isNull(), isNull(), isNull(), any()))
                .thenReturn(PageResponse.of(page));

        mvc.perform(get("/api/products").param("name", "Product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3));
    }

    @Test
    void should_return_200_when_get_product_by_id() throws Exception {
        when(productService.getById(5L)).thenReturn(sampleProduct(5L));

        mvc.perform(get("/api/products/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.sku").value("SKU-5"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void should_return_404_when_product_not_found_or_inactive() throws Exception {
        when(productService.getById(99L))
                .thenThrow(new ResourceNotFoundException("Producto no encontrado o inactivo"));

        mvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    // ─── P4 contract: images field absent in list, present in detail ────────────

    @Test
    void should_not_include_images_field_in_list_response() throws Exception {
        var page = new PageImpl<>(List.of(sampleProduct(1L)), PageRequest.of(0, 20), 1);
        when(productService.search(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(PageResponse.of(page));

        // images field must be absent (null → omitted via @JsonInclude(NON_NULL))
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].images").doesNotExist());
    }

    @Test
    void should_include_images_array_in_detail_response() throws Exception {
        when(productService.getById(5L)).thenReturn(sampleProductWithImages(5L));

        mvc.perform(get("/api/products/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images").isArray())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].id").value(10))
                .andExpect(jsonPath("$.images[0].url").value("http://localhost:8080/api/uploads/images/a.jpg"))
                .andExpect(jsonPath("$.images[0].displayOrder").value(0))
                .andExpect(jsonPath("$.images[0].cover").value(true))
                .andExpect(jsonPath("$.images[1].cover").value(false));
    }
}
