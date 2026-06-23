package pe.com.krypton.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.dto.response.CategoryResponse;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.CategoryService;

/**
 * Web slice: solo la capa HTTP de CategoryController.
 * Seguridad desactivada (addFilters=false) — verificar 401/403 en integración (Phase 5).
 */
@WebMvcTest(controllers = CategoryController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CategoryService categoryService;

    private CategoryResponse sample(Long id) {
        return new CategoryResponse(id, "Category " + id, "Description " + id);
    }

    @Test
    void should_return_200_and_list_when_listing_categories() throws Exception {
        when(categoryService.list()).thenReturn(List.of(sample(1L), sample(2L), sample(3L)));

        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Category 1"));
    }

    @Test
    void should_return_200_when_get_category_by_id() throws Exception {
        when(categoryService.getById(2L)).thenReturn(sample(2L));

        mvc.perform(get("/api/categories/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Category 2"));
    }

    @Test
    void should_return_404_when_category_not_found() throws Exception {
        when(categoryService.getById(99L))
                .thenThrow(new ResourceNotFoundException("Categoría no encontrada"));

        mvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound());
    }
}
