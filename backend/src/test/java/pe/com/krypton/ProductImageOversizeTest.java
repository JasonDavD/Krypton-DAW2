package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MySQLContainer;

/**
 * Tests the 413 Payload Too Large scenario for oversize file uploads.
 *
 * ADR-D6: this scenario requires a REAL HTTP server (RANDOM_PORT), because
 * MockMvc bypasses Spring's StandardServletMultipartResolver — MockMultipartFile
 * skips the multipart parsing step where MaxUploadSizeExceededException is thrown.
 *
 * This class uses its OWN container (NOT the singleton from AbstractIntegrationTest)
 * to isolate the RANDOM_PORT context from the MOCK context used by the singleton.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ProductImageOversizeTest {

    // Own container for RANDOM_PORT context (cannot share the AbstractIntegrationTest singleton
    // because webEnvironment RANDOM_PORT creates a separate Spring context)
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8");

    static {
        MYSQL.start();
    }

    @TempDir
    static Path uploadsDir;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.uploads.dir", () -> uploadsDir.toAbsolutePath().toString());
        registry.add("app.uploads.base-url", () -> "http://localhost");
        registry.add("spring.servlet.multipart.max-file-size", () -> "5MB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "6MB");
    }

    @Autowired
    TestRestTemplate restTemplate;

    private String adminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(
                "{\"email\":\"admin@krypton.pe\",\"password\":\"Admin123!\"}", headers);
        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response =
                restTemplate.exchange("/api/auth/login", HttpMethod.POST, entity,
                        com.fasterxml.jackson.databind.JsonNode.class);
        return response.getBody().get("token").asText();
    }

    /**
     * 7 MB upload exceeds the 6MB max-request-size.
     * Tomcat sends HTTP 413 and closes the connection before the client finishes uploading.
     * This means either:
     * (a) The response is received with status 413, OR
     * (b) An I/O exception is thrown because Tomcat reset the connection mid-upload.
     *
     * Both outcomes confirm the oversize rejection works as designed (ADR-D6).
     * We catch the ResourceAccessException (connection reset) as equivalent to 413.
     */
    @Test
    void upload_7mb_file_is_rejected_by_server() {
        String token = adminToken();

        byte[] bigData = new byte[7 * 1024 * 1024];

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bigData) {
            @Override
            public String getFilename() { return "big.jpg"; }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/admin/products/1/images",
                    HttpMethod.POST,
                    requestEntity,
                    String.class);
            // If server responds before connection reset: must be 413
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            // Tomcat closed the connection mid-upload — the server DID reject the payload.
            // This is the expected outcome for oversized uploads with Tomcat's connector.
            assertThat(ex.getMessage())
                    .as("Expected connection reset or I/O error indicating server rejection")
                    .isNotNull();
        }
    }
}

