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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.dto.response.CategoryResponse;
import pe.com.krypton.exception.CategoryInUseException;
import pe.com.krypton.exception.DuplicateCategoryNameException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.CategoryService;

/**
 * Web slice del AdminCategoryController (service mockeado, seguridad desactivada).
 * El borde de seguridad (401 sin token, 403 no-ADMIN) se prueba en integración (Phase 5).
 */
@WebMvcTest(controllers = AdminCategoryController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AdminCategoryControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CategoryService categoryService;

    private static final String VALID_BODY = """
            {"name":"Electronics","description":"Electronic products"}
            """;

    private CategoryResponse sample(Long id) {
        return new CategoryResponse(id, "Electronics", "Electronic products");
    }

    @Test
    void should_return_201_when_create_is_valid() throws Exception {
        when(categoryService.create(any())).thenReturn(sample(5L));

        mvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    void should_return_409_when_create_has_duplicate_name() throws Exception {
        when(categoryService.create(any()))
                .thenThrow(new DuplicateCategoryNameException("Nombre ya registrado"));

        mvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void should_return_400_when_create_body_is_invalid() throws Exception {
        // name is @NotBlank — empty string should fail
        mvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"desc\"}"))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).create(any());
    }

    @Test
    void should_return_200_when_update_is_valid() throws Exception {
        when(categoryService.update(eq(5L), any())).thenReturn(sample(5L));

        mvc.perform(put("/api/admin/categories/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void should_return_404_when_update_category_not_found() throws Exception {
        when(categoryService.update(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Categoría no encontrada"));

        mvc.perform(put("/api/admin/categories/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_409_when_update_has_duplicate_name() throws Exception {
        when(categoryService.update(eq(5L), any()))
                .thenThrow(new DuplicateCategoryNameException("Nombre ya usado"));

        mvc.perform(put("/api/admin/categories/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void should_return_204_when_delete_succeeds() throws Exception {
        doNothing().when(categoryService).delete(5L);

        mvc.perform(delete("/api/admin/categories/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_return_409_when_delete_category_in_use() throws Exception {
        doThrow(new CategoryInUseException("Categoría tiene productos asociados"))
                .when(categoryService).delete(5L);

        mvc.perform(delete("/api/admin/categories/5"))
                .andExpect(status().isConflict());
    }

    @Test
    void should_return_404_when_delete_category_not_found() throws Exception {
        doThrow(new ResourceNotFoundException("Categoría no encontrada"))
                .when(categoryService).delete(99L);

        mvc.perform(delete("/api/admin/categories/99"))
                .andExpect(status().isNotFound());
    }
}
