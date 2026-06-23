package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.com.krypton.repository.CategoryRepository;
import pe.com.krypton.repository.ProductRepository;

/**
 * Integración end-to-end del catálogo: Postgres real (Testcontainers singleton),
 * cadena JWT completa activada. Cubre tareas 5.1–5.6 del SDD catalogo.
 *
 * Aislamiento: @AfterEach limpia products → categories en orden FK para que
 * las filas de catálogo nunca contaminen los demás tests de integración que
 * comparten el mismo contenedor singleton.
 */
@AutoConfigureMockMvc
class CatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String ADMIN_EMAIL    = "admin@krypton.pe";
    private static final String ADMIN_PASSWORD = "Admin123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    // ------------------------------------------------------------------ helpers

    /** Autentica al admin sembrado en V3 y devuelve el token JWT. */
    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    /** Autentica (o registra+autentica) un usuario CLIENTE y devuelve su token JWT. */
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Crea una categoría vía ADMIN y devuelve su id. */
    private long createCategory(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/admin/categories").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    /** Crea un producto vía ADMIN y devuelve su id. */
    private long createProduct(String token, String sku, String name, long categoryId) throws Exception {
        String payload = String.format(
                "{\"sku\":\"%s\",\"name\":\"%s\",\"price\":99.90,\"stock\":10,\"categoryId\":%d}",
                sku, name, categoryId);
        String body = mvc.perform(post("/api/admin/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    // ------------------------------------------------------------------ cleanup (task 5.6)

    /**
     * Limpieza en orden FK: products primero, luego categories.
     * Scope acotado: solo filas creadas en esta clase (distinguidas por un prefijo de SKU/nombre).
     * El admin seed (V3) y filas de otras clases no se tocan.
     */
    @AfterEach
    void cleanupCatalogRows() {
        // Soft-deleted o activos, todos deben eliminarse físicamente para no acumular datos
        productRepository.findAll().stream()
                .filter(p -> p.getSku() != null && p.getSku().startsWith("IT-"))
                .forEach(p -> productRepository.delete(p));
        categoryRepository.findAll().stream()
                .filter(c -> c.getName() != null && c.getName().startsWith("IT-"))
                .forEach(c -> categoryRepository.delete(c));
    }

    // ------------------------------------------------------------------ task 5.1: public GET sin token

    @Test
    void public_get_products_returns_200_without_token() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void public_get_categories_returns_200_without_token() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void public_get_only_returns_active_products() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IT-Active-Cat");
        long active = createProduct(token, "IT-ACTIVE-01", "IT-Visible", catId);

        // Soft-delete deja active=false
        mvc.perform(delete("/api/admin/products/" + active)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        // El producto borrado (active=false) no debe aparecer en la lista pública
        MvcResult result = mvc.perform(get("/api/products").param("name", "IT-Visible"))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(objectMapper.readTree(json).get("totalElements").asLong()).isZero();
    }

    @Test
    void get_inactive_product_by_id_returns_404() throws Exception {
        String token = adminToken();
        long catId = createCategory(token, "IT-Inactive-Cat");
        long prodId = createProduct(token, "IT-INACTIVE-01", "IT-InvisibleProd", catId);

        // Soft-delete
        mvc.perform(delete("/api/admin/products/" + prodId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        // GET por id de un producto inactivo → 404
        mvc.perform(get("/api/products/" + prodId))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ task 5.2: filtros compuestos

    @Test
    void filter_by_name_substring_returns_matching_products() throws Exception {
        String token = adminToken();
        long catId = createCategory(token, "IT-Filter-Cat");
        createProduct(token, "IT-FILTER-01", "IT-FilterTarget", catId);
        createProduct(token, "IT-FILTER-02", "IT-OtherProduct", catId);

        mvc.perform(get("/api/products").param("name", "FilterTarget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("IT-FILTER-01"));
    }

    @Test
    void filter_by_category_id_returns_only_that_category() throws Exception {
        String token = adminToken();
        long catA = createCategory(token, "IT-CatA");
        long catB = createCategory(token, "IT-CatB");
        createProduct(token, "IT-CATFLT-01", "IT-ProdCatA", catA);
        createProduct(token, "IT-CATFLT-02", "IT-ProdCatB", catB);

        mvc.perform(get("/api/products").param("categoryId", String.valueOf(catA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("IT-CATFLT-01"));
    }

    @Test
    void filter_by_price_range_returns_correct_subset() throws Exception {
        String token = adminToken();
        long catId = createCategory(token, "IT-PriceRange-Cat");

        // precio 50
        String low = String.format(
                "{\"sku\":\"IT-PRICE-01\",\"name\":\"IT-Cheap\",\"price\":50.00,\"stock\":5,\"categoryId\":%d}", catId);
        // precio 200
        String high = String.format(
                "{\"sku\":\"IT-PRICE-02\",\"name\":\"IT-Expensive\",\"price\":200.00,\"stock\":5,\"categoryId\":%d}", catId);

        mvc.perform(post("/api/admin/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).content(low))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).content(high))
                .andExpect(status().isCreated());

        // Filtro: 100–300 → solo el caro
        mvc.perform(get("/api/products")
                        .param("priceMin", "100")
                        .param("priceMax", "300")
                        .param("name", "IT-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("IT-PRICE-02"));
    }

    @Test
    void pagination_metadata_is_correct() throws Exception {
        String token = adminToken();
        long catId = createCategory(token, "IT-Page-Cat");
        createProduct(token, "IT-PAGE-01", "IT-PagProd1", catId);
        createProduct(token, "IT-PAGE-02", "IT-PagProd2", catId);
        createProduct(token, "IT-PAGE-03", "IT-PagProd3", catId);

        mvc.perform(get("/api/products")
                        .param("name", "IT-PagProd")
                        .param("size", "2")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    // ------------------------------------------------------------------ task 5.3: validaciones ADMIN products

    @Test
    void admin_create_product_with_duplicate_sku_returns_409() throws Exception {
        String token = adminToken();
        long catId = createCategory(token, "IT-DupSku-Cat");
        createProduct(token, "IT-DUPSKU-01", "IT-Original", catId);

        String dup = String.format(
                "{\"sku\":\"IT-DUPSKU-01\",\"name\":\"IT-Duplicate\",\"price\":10.00,\"stock\":1,\"categoryId\":%d}",
                catId);
        mvc.perform(post("/api/admin/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content(dup))
                .andExpect(status().isConflict());
    }

    @Test
    void admin_create_product_missing_required_field_returns_400() throws Exception {
        String token = adminToken();
        // sin sku
        mvc.perform(post("/api/admin/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content("{\"name\":\"IT-NoSku\",\"price\":10.00,\"stock\":1,\"categoryId\":1}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ task 5.4: category delete guard

    @Test
    void delete_category_with_products_returns_409() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IT-InUse-Cat");
        createProduct(token, "IT-INUSE-01", "IT-ProductInCat", catId);

        mvc.perform(delete("/api/admin/categories/" + catId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_empty_category_returns_204() throws Exception {
        String token = adminToken();
        long catId = createCategory(token, "IT-Empty-Cat");

        mvc.perform(delete("/api/admin/categories/" + catId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_category_with_soft_deleted_product_still_returns_409() throws Exception {
        String token = adminToken();
        long catId  = createCategory(token, "IT-SoftDel-Cat");
        long prodId = createProduct(token, "IT-SOFTDEL-01", "IT-SoftProduct", catId);

        // Soft-delete del producto: queda active=false pero la fila persiste con su FK category_id.
        mvc.perform(delete("/api/admin/products/" + prodId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        // El guard usa existsByCategoryId, que cuenta TODAS las filas (activas e inactivas).
        // Un producto soft-deleted sigue referenciando la categoría por la FK NOT NULL, así que
        // borrar la categoría DEBE seguir devolviendo 409 — decisión deliberada que evita la
        // violación de FK que provocaría el hard-delete con filas huérfanas (ver engram #298).
        mvc.perform(delete("/api/admin/categories/" + catId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ task 5.5: autorización

    @Test
    void unauthenticated_request_to_admin_products_returns_401() throws Exception {
        mvc.perform(post("/api/admin/products").contentType(JSON)
                        .content("{\"sku\":\"IT-X\",\"name\":\"IT-X\",\"price\":1,\"stock\":1,\"categoryId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_request_to_admin_categories_returns_401() throws Exception {
        mvc.perform(post("/api/admin/categories").contentType(JSON)
                        .content("{\"name\":\"IT-Unauthorized\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cliente_token_on_admin_products_returns_403() throws Exception {
        String clientTok = clientToken(
                "catalog-client-" + System.nanoTime() + "@krypton.pe", "Secret123");

        mvc.perform(post("/api/admin/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientTok))
                        .content("{\"sku\":\"IT-X2\",\"name\":\"IT-X2\",\"price\":1,\"stock\":1,\"categoryId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cliente_token_on_admin_categories_returns_403() throws Exception {
        String clientTok = clientToken(
                "catalog-client2-" + System.nanoTime() + "@krypton.pe", "Secret123");

        mvc.perform(post("/api/admin/categories").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientTok))
                        .content("{\"name\":\"IT-Forbidden\"}"))
                .andExpect(status().isForbidden());
    }
}
