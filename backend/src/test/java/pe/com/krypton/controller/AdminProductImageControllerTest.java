package pe.com.krypton.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.ProductImageService;

/**
 * Web slice for AdminProductImageController.
 * Security is disabled (addFilters=false) — 401/403 is tested in integration (Phase 5).
 * ADR-D6: 413 (oversize) is NOT testable in the web slice — only in integration.
 * The slice asserts the service-level 400 for invalid type only.
 */
@WebMvcTest(controllers = AdminProductImageController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AdminProductImageControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ProductImageService productImageService;

    private static final String BASE = "/api/admin/products/1/images";

    // ─── POST upload ────────────────────────────────────────────────────────────

    @Test
    void should_return_201_when_upload_is_valid() throws Exception {
        doNothing().when(productImageService).upload(eq(1L), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "data".getBytes());

        mvc.perform(multipart(BASE).file(file))
                .andExpect(status().isCreated());
    }

    @Test
    void should_return_400_when_upload_type_is_invalid() throws Exception {
        doThrow(new IllegalArgumentException("Tipo de archivo no permitido"))
                .when(productImageService).upload(eq(1L), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "file.txt", "text/plain", "data".getBytes());

        mvc.perform(multipart(BASE).file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_404_when_product_not_found_on_upload() throws Exception {
        doThrow(new ResourceNotFoundException("Producto no encontrado: 1"))
                .when(productImageService).upload(eq(1L), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "data".getBytes());

        mvc.perform(multipart(BASE).file(file))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE ─────────────────────────────────────────────────────────────────

    @Test
    void should_return_204_when_delete_image_succeeds() throws Exception {
        doNothing().when(productImageService).delete(1L, 5L);

        mvc.perform(delete(BASE + "/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_return_404_when_delete_image_not_found() throws Exception {
        doThrow(new ResourceNotFoundException("Imagen no encontrada: 99"))
                .when(productImageService).delete(eq(1L), eq(99L));

        mvc.perform(delete(BASE + "/99"))
                .andExpect(status().isNotFound());
    }

    // ─── PATCH reorder ──────────────────────────────────────────────────────────

    @Test
    void should_return_200_when_reorder_is_valid() throws Exception {
        doNothing().when(productImageService).reorder(eq(1L), any());

        mvc.perform(patch(BASE + "/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 2, 3]"))
                .andExpect(status().isOk());
    }

    @Test
    void should_return_400_when_reorder_has_foreign_id() throws Exception {
        doThrow(new IllegalArgumentException("El conjunto de IDs no coincide"))
                .when(productImageService).reorder(eq(1L), any());

        mvc.perform(patch(BASE + "/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 999]"))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH cover ────────────────────────────────────────────────────────────

    @Test
    void should_return_200_when_set_cover_succeeds() throws Exception {
        doNothing().when(productImageService).setCover(1L, 3L);

        mvc.perform(patch(BASE + "/3/cover"))
                .andExpect(status().isOk());
    }

    @Test
    void should_return_404_when_set_cover_image_not_found() throws Exception {
        doThrow(new ResourceNotFoundException("Imagen no encontrada: 99"))
                .when(productImageService).setCover(eq(1L), eq(99L));

        mvc.perform(patch(BASE + "/99/cover"))
                .andExpect(status().isNotFound());
    }
}
