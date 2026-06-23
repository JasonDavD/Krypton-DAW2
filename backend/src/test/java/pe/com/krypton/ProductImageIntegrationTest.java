package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.com.krypton.repository.ProductImageRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.CategoryRepository;

/**
 * Full end-to-end integration tests for the product image feature.
 *
 * Key patterns (design §8.2):
 * - @TempDir is STATIC so it is visible to @DynamicPropertySource (which is also static).
 *   A field-level (instance) @TempDir is initialised too late — DynamicPropertySource runs
 *   before any instance is created. This is the "static @TempDir" pattern mandated by ADR-D3.
 * - Singleton container (AbstractIntegrationTest) — one Postgres JVM-wide.
 * - @AfterEach cleans product_image rows first (FK), then products, then categories.
 * - ADR-D6: 413 (payload too large) is only reproducible here, not in @WebMvcTest slices.
 */
@AutoConfigureMockMvc
class ProductImageIntegrationTest extends AbstractIntegrationTest {

    // ─── Static @TempDir for @DynamicPropertySource ──────────────────────────────
    @TempDir
    static Path uploadsDir;

    @DynamicPropertySource
    static void overrideUploadsDir(DynamicPropertyRegistry registry) {
        registry.add("app.uploads.dir", () -> uploadsDir.toAbsolutePath().toString());
        registry.add("app.uploads.base-url", () -> "http://localhost");
        // Keep multipart limits consistent with application.yml
        registry.add("spring.servlet.multipart.max-file-size", () -> "5MB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "6MB");
    }

    // ─── Constants ───────────────────────────────────────────────────────────────
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String ADMIN_EMAIL    = "admin@krypton.pe";
    private static final String ADMIN_PASSWORD = "Admin123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProductImageRepository productImageRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String clientToken(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"name\":\"Cliente\",\"email\":\"" + email
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private long createCategory(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/admin/categories").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createProduct(String token, String sku, String name, long categoryId) throws Exception {
        String payload = String.format(
                "{\"sku\":\"%s\",\"name\":\"%s\",\"price\":99.90,\"stock\":5,\"categoryId\":%d}",
                sku, name, categoryId);
        String body = mvc.perform(post("/api/admin/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long uploadImage(String token, long productId, byte[] content, String contentType)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", contentType, content);
        MvcResult result = mvc.perform(
                        multipart("/api/admin/products/" + productId + "/images")
                                .file(file)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        // Retrieve the product detail to get the latest image id
        String detail = mvc.perform(
                        get("/api/products/" + productId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Return the last image's id
        var images = objectMapper.readTree(detail).get("images");
        return images.get(images.size() - 1).get("id").asLong();
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────────

    /**
     * FK order: product_image → products → categories.
     * Scope: only rows created in this test class (prefix "IMG-").
     */
    @AfterEach
    void cleanupImageRows() {
        // Delete product_image rows for our products
        productRepository.findAll().stream()
                .filter(p -> p.getSku() != null && p.getSku().startsWith("IMG-"))
                .forEach(p -> {
                    productImageRepository.findByProductId(p.getId())
                            .forEach(img -> productImageRepository.delete(img));
                    productRepository.delete(p);
                });
        categoryRepository.findAll().stream()
                .filter(c -> c.getName() != null && c.getName().startsWith("IMG-"))
                .forEach(c -> categoryRepository.delete(c));
    }

    // ─── Upload → DB row + file on disk + imageUrl sync ──────────────────────────

    @Test
    void upload_creates_db_row_and_physical_file_and_syncs_image_url() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IMG-Cat1");
        long prodId = createProduct(token, "IMG-P1", "IMG-Product1", catId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg",
                "JPEG_FAKE_DATA".getBytes());

        mvc.perform(multipart("/api/admin/products/" + prodId + "/images")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated());

        // Assert DB row
        var images = productImageRepository.findByProductId(prodId);
        assertThat(images).hasSize(1);
        String storedFilename = images.get(0).getPath();
        assertThat(storedFilename).endsWith(".jpg");

        // Assert file physically on disk
        Path physicalFile = uploadsDir.resolve(storedFilename);
        assertThat(Files.exists(physicalFile)).isTrue();

        // Assert product.imageUrl synced
        mvc.perform(get("/api/products/" + prodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(
                        org.hamcrest.Matchers.startsWith("http://localhost")));
    }

    // ─── GET /api/uploads/images/{filename} → 200 + body ─────────────────────────

    @Test
    void serve_returns_200_with_body_after_upload() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IMG-Cat2");
        long prodId = createProduct(token, "IMG-P2", "IMG-Product2", catId);

        byte[] imageData = "FAKE_IMAGE_BYTES".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", imageData);

        mvc.perform(multipart("/api/admin/products/" + prodId + "/images")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated());

        String filename = productImageRepository.findByProductId(prodId).get(0).getPath();

        mvc.perform(get("/api/uploads/images/" + filename))
                .andExpect(status().isOk())
                .andExpect(content().bytes(imageData))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("max-age=604800")));
    }

    // ─── Path-traversal end-to-end ────────────────────────────────────────────────

    @Test
    void path_traversal_with_dotdot_is_rejected_400() throws Exception {
        // A crafted ../etc/passwd filename must NOT escape the uploads dir.
        // Note: URL-decoded path traversal hits ImageServeController's guard first.
        mvc.perform(get("/api/uploads/images/..%2Fetc%2Fpasswd"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void path_traversal_with_dotdot_prefix_is_rejected_400() throws Exception {
        mvc.perform(get("/api/uploads/images/..secret.jpg"))
                .andExpect(status().isBadRequest());
    }

    // ─── File size validation: >5MB → 400 from service layer ────────────────────
    //
    // ADR-D6: true 413 (from the multipart resolver) requires RANDOM_PORT + TestRestTemplate.
    // MockMvc bypasses StandardServletMultipartResolver — MockMultipartFile skips multipart
    // parsing, so MaxUploadSizeExceededException is never thrown here.
    // The service-level check (file.getSize() > 5MB → IllegalArgumentException → 400) is
    // tested here. The real 413 is covered by ProductImageOversizeTest (RANDOM_PORT).

    @Test
    void upload_exceeding_5mb_returns_400_via_service_size_check() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IMG-Cat3");
        long prodId = createProduct(token, "IMG-P3", "IMG-Product3", catId);

        // 5MB + 1 byte — exceeds the service-level MAX_FILE_SIZE_BYTES (5MB).
        // ProductImageService.upload() throws IllegalArgumentException → 400.
        byte[] bigData = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", bigData);

        mvc.perform(multipart("/api/admin/products/" + prodId + "/images")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    // ─── Cover lifecycle: delete cover → promote second ──────────────────────────

    @Test
    void delete_cover_promotes_second_image_and_updates_image_url() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IMG-Cat4");
        long prodId = createProduct(token, "IMG-P4", "IMG-Product4", catId);

        // Upload two images; first is cover
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", "A".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "b.jpg", "image/jpeg", "B".getBytes());

        mvc.perform(multipart("/api/admin/products/" + prodId + "/images")
                        .file(file1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/admin/products/" + prodId + "/images")
                        .file(file2)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated());

        var images = productImageRepository.findByProductId(prodId);
        assertThat(images).hasSize(2);
        long coverId    = images.stream().filter(i -> i.isCover()).findFirst().orElseThrow().getId();
        long nonCoverId = images.stream().filter(i -> !i.isCover()).findFirst().orElseThrow().getId();
        String nonCoverPath = images.stream().filter(i -> !i.isCover()).findFirst().orElseThrow().getPath();

        // Delete the cover
        mvc.perform(delete("/api/admin/products/" + prodId + "/images/" + coverId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        // Assert promotion
        var remaining = productImageRepository.findByProductId(prodId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).isCover()).isTrue();

        // Assert product.imageUrl updated to the promoted image's URL
        mvc.perform(get("/api/products/" + prodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(
                        org.hamcrest.Matchers.containsString(nonCoverPath)));
    }

    // ─── Cover lifecycle: delete last → imageUrl = null ──────────────────────────

    @Test
    void delete_last_image_sets_image_url_to_null() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IMG-Cat5");
        long prodId = createProduct(token, "IMG-P5", "IMG-Product5", catId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "only.jpg", "image/jpeg", "DATA".getBytes());

        mvc.perform(multipart("/api/admin/products/" + prodId + "/images")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated());

        long imageId = productImageRepository.findByProductId(prodId).get(0).getId();

        mvc.perform(delete("/api/admin/products/" + prodId + "/images/" + imageId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        // product.imageUrl must be null now
        mvc.perform(get("/api/products/" + prodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    // ─── Authorization: 401/403 ───────────────────────────────────────────────────

    @Test
    void unauthenticated_upload_returns_401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "DATA".getBytes());

        mvc.perform(multipart("/api/admin/products/1/images").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void client_token_on_upload_returns_403() throws Exception {
        String clientTok = clientToken(
                "img-client-" + System.nanoTime() + "@krypton.pe", "Secret123");
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "DATA".getBytes());

        mvc.perform(multipart("/api/admin/products/1/images")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientTok)))
                .andExpect(status().isForbidden());
    }

    // ─── Reorder with foreign ID → 400 (D1) ──────────────────────────────────────

    @Test
    void reorder_with_foreign_id_returns_400() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IMG-Cat6");
        long prodId = createProduct(token, "IMG-P6", "IMG-Product6", catId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "DATA".getBytes());

        mvc.perform(multipart("/api/admin/products/" + prodId + "/images")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated());

        long realId = productImageRepository.findByProductId(prodId).get(0).getId();
        long foreignId = realId + 99999;

        mvc.perform(patch("/api/admin/products/" + prodId + "/images/reorder")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(JSON)
                        .content("[" + realId + "," + foreignId + "]"))
                .andExpect(status().isBadRequest());
    }

    // ─── Serve: 404 for unknown filename ─────────────────────────────────────────

    @Test
    void serve_unknown_filename_returns_404() throws Exception {
        mvc.perform(get("/api/uploads/images/nonexistent-00000000.jpg"))
                .andExpect(status().isNotFound());
    }

    // ─── setCover round-trip ─────────────────────────────────────────────────────
    //
    // REGRESSION COVERAGE for WARNING-1 (verify-report):
    // setCover() must flush between demoting the current cover (is_cover=false) and
    // promoting the target (is_cover=true). Without the flush, Hibernate may reorder
    // both UPDATEs in a single flush cycle, hitting the partial unique index
    // UNIQUE (product_id) WHERE is_cover = TRUE with two cover rows simultaneously.
    // This is the same race that was fixed in delete() (ADR-D5).
    //
    // NOTE: because the partial-unique violation is a nondeterministic Hibernate
    // reordering issue, this test verifies the correct END-STATE and correct
    // products.image_url sync — it is not a deterministic race reproducer. The fix
    // (entityManager.flush() after demote) makes the ordering explicit and eliminates
    // the race at the database level regardless of Hibernate's internal reordering.

    @Test
    void setCover_promotes_target_and_demotes_previous_cover_and_syncs_image_url() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IMG-CatSC");
        long prodId = createProduct(token, "IMG-PSC", "IMG-ProductSC", catId);

        // Upload image A (becomes first cover automatically)
        long imageAId = uploadImage(token, prodId, "IMAGE_A".getBytes(), "image/jpeg");
        // Upload image B (non-cover)
        long imageBId = uploadImage(token, prodId, "IMAGE_B".getBytes(), "image/jpeg");

        // Precondition: image A is the cover
        var before = productImageRepository.findByProductId(prodId);
        assertThat(before).hasSize(2);
        assertThat(before.stream().filter(i -> i.isCover()).map(i -> i.getId()).findFirst())
                .contains(imageAId);
        String imageBPath = before.stream()
                .filter(i -> i.getId().equals(imageBId))
                .findFirst().orElseThrow().getPath();

        // PATCH cover to image B
        mvc.perform(patch("/api/admin/products/" + prodId + "/images/" + imageBId + "/cover")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        // Assert: image B is now cover, image A is NOT cover
        var after = productImageRepository.findByProductId(prodId);
        assertThat(after.stream().filter(i -> i.isCover()).map(i -> i.getId()).toList())
                .as("Exactly one cover row, and it must be image B")
                .containsExactly(imageBId);
        assertThat(after.stream().filter(i -> i.getId().equals(imageAId)).findFirst().orElseThrow().isCover())
                .as("Image A must no longer be cover")
                .isFalse();

        // Assert: exactly ONE cover row in DB for this product
        long coverCount = after.stream().filter(i -> i.isCover()).count();
        assertThat(coverCount)
                .as("There must be exactly one cover row per product after setCover")
                .isEqualTo(1L);

        // Assert: product.image_url synced to image B's serving URL
        mvc.perform(get("/api/products/" + prodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(
                        org.hamcrest.Matchers.containsString(imageBPath)));
    }
}
