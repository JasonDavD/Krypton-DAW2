package pe.com.krypton;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integración CORS: verifica que el preflight OPTIONS desde el dev server de Vite/React
 * (http://localhost:5173) recibe los encabezados correctos, y que orígenes no
 * permitidos no los obtienen. Usa la cadena de seguridad real (Testcontainers +
 * Spring Security full chain) — un @WebMvcTest con addFilters=false no serviría
 * porque CORS lo resuelven los filtros de Security.
 */
@AutoConfigureMockMvc
class CorsConfigIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String REJECTED_ORIGIN = "http://attacker.example.com";

    @Test
    void preflight_from_vite_dev_server_returns_cors_headers() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsString("Authorization")));
    }

    @Test
    void preflight_from_unlisted_origin_does_not_return_allow_origin_header() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, REJECTED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
