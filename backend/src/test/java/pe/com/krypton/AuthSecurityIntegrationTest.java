package pe.com.krypton;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integración del borde de seguridad: Postgres real (Testcontainers) + cadena JWT
 * completa. Aquí se prueba lo que el web slice no puede: 401/403 reales, el admin
 * sembrado en V3 y la baja inmediata de un usuario con token vigente.
 */
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void public_register_is_allowed_without_token() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"name\":\"Pia\",\"email\":\"pia@krypton.pe\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void protected_endpoint_without_token_is_401() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seeded_admin_can_login_and_list_users() throws Exception {
        String adminToken = login("admin@krypton.pe", "Admin123!");

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void client_token_is_forbidden_on_admin_area() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"name\":\"Cli\",\"email\":\"cli@krypton.pe\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated());
        String clientToken = login("cli@krypton.pe", "Secret123");

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivated_user_is_rejected_immediately() throws Exception {
        String adminToken = login("admin@krypton.pe", "Admin123!");

        // El admin crea un segundo admin
        String created = mvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).contentType(JSON)
                        .content("{\"name\":\"Admin2\",\"email\":\"admin2@krypton.pe\",\"password\":\"Secret123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long admin2Id = objectMapper.readTree(created).get("id").asLong();

        // admin2 entra y accede OK
        String token2 = login("admin2@krypton.pe", "Secret123");
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token2)))
                .andExpect(status().isOk());

        // El admin original lo da de baja lógica
        mvc.perform(patch("/api/admin/users/" + admin2Id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).contentType(JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        // El token de admin2 (aún no vencido) ahora es rechazado → baja inmediata
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token2)))
                .andExpect(status().isUnauthorized());
    }
}
