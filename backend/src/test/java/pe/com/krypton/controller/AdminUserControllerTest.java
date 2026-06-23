package pe.com.krypton.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.dto.response.UserResponse;
import pe.com.krypton.exception.DuplicateEmailException;
import pe.com.krypton.exception.LastAdminException;
import pe.com.krypton.model.enums.Role;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.UserService;

/**
 * Web slice del AdminUserController (service mockeado, seguridad desactivada).
 * El borde de seguridad (401 sin token, 403 CLIENTE) se prueba en integración (Phase 6).
 */
@WebMvcTest(controllers = AdminUserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AdminUserControllerTest {

    @Autowired MockMvc mvc;
    @MockBean UserService userService;

    private UserResponse sample(Long id, Role role, boolean active) {
        return new UserResponse(id, "U" + id, "u" + id + "@krypton.pe", role, active, Instant.now());
    }

    @Test
    void should_return_200_and_list_without_password() throws Exception {
        when(userService.listAll()).thenReturn(List.of(sample(1L, Role.ADMIN, true), sample(2L, Role.CLIENTE, true)));

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void should_return_201_when_admin_creates_user() throws Exception {
        when(userService.create(any())).thenReturn(sample(9L, Role.ADMIN, true));

        mvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo\",\"email\":\"nuevo@krypton.pe\",\"password\":\"Secret123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void should_return_409_when_create_email_duplicated() throws Exception {
        when(userService.create(any())).thenThrow(new DuplicateEmailException("dup"));

        mvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"dup@krypton.pe\",\"password\":\"Secret123\",\"role\":\"CLIENTE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void should_return_200_when_change_role() throws Exception {
        when(userService.changeRole(eq(5L), eq(Role.ADMIN))).thenReturn(sample(5L, Role.ADMIN, true));

        mvc.perform(patch("/api/admin/users/5/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void should_return_422_when_demoting_last_admin() throws Exception {
        when(userService.changeRole(eq(1L), eq(Role.CLIENTE)))
                .thenThrow(new LastAdminException("No se puede degradar al último administrador activo"));

        mvc.perform(patch("/api/admin/users/1/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CLIENTE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_return_200_when_set_status() throws Exception {
        when(userService.setStatus(eq(7L), eq(false))).thenReturn(sample(7L, Role.CLIENTE, false));

        mvc.perform(patch("/api/admin/users/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void should_return_400_when_create_body_is_invalid() throws Exception {
        mvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"x@krypton.pe\",\"password\":\"Secret123\"}")) // sin role
                .andExpect(status().isBadRequest());

        verify(userService, never()).create(any());
    }
}
