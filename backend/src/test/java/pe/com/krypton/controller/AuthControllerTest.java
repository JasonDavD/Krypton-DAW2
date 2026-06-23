package pe.com.krypton.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.dto.response.AuthResponse;
import pe.com.krypton.dto.response.UserResponse;
import pe.com.krypton.exception.DuplicateEmailException;
import pe.com.krypton.exception.InvalidCredentialsException;
import pe.com.krypton.model.enums.Role;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.AuthService;

/**
 * Web slice: solo la capa HTTP del AuthController. El service va mockeado y la
 * cadena de seguridad se desactiva (addFilters=false) para probar el controller
 * aislado — status codes, validación y contrato JSON.
 */
@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class)) // el filtro JWT no va en el slice
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AuthService authService;

    @Test
    void should_return_201_and_hide_password_when_register_is_valid() throws Exception {
        when(authService.register(any())).thenReturn(
                new UserResponse(1L, "Ana", "ana@krypton.pe", Role.CLIENTE, true, Instant.now()));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@krypton.pe\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENTE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void should_return_409_when_email_is_duplicated() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateEmailException("El email ya está registrado"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@krypton.pe\",\"password\":\"Secret123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void should_return_400_when_register_body_is_invalid() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"password\":\"x\"}")) // falta email
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void should_return_200_and_token_when_login_is_valid() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-token", "Bearer", 86400));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@krypton.pe\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void should_return_401_when_login_credentials_are_invalid() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("Credenciales inválidas"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@krypton.pe\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }
}
