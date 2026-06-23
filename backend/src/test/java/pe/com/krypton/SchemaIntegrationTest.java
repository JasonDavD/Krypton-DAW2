package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica que las migraciones Flyway crean el schema esperado
 * sobre un MySQL real (Testcontainers, NO H2 — paridad con prod).
 * Introspección vía information_schema; DATABASE() = el schema actual.
 */
class SchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void v1_creates_the_eight_model_tables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).contains(
                "users", "categories", "products", "cart",
                "cart_item", "orders", "order_items", "stock_movement");
    }

    @Test
    void v2_adds_active_column_to_users() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'users'",
                String.class);

        assertThat(columns).contains("active");
    }

    // ─── V5: product_image table + indexes ──────────────────────────────────────

    @Test
    void v5_creates_product_image_table() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).contains("product_image");
    }

    @Test
    void v5_product_image_has_expected_columns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'product_image'",
                String.class);

        assertThat(columns).contains("id", "product_id", "path", "display_order", "is_cover", "created_at");
    }

    @Test
    void v5_creates_idx_product_image_product() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'product_image'",
                String.class);

        assertThat(indexes).contains("idx_product_image_product");
    }

    /**
     * MySQL no soporta índices parciales (el {@code WHERE is_cover = TRUE} de
     * PostgreSQL). El invariante "una sola portada por producto" se garantiza con
     * una columna generada {@code cover_key} (= product_id solo si is_cover, NULL
     * si no) + un índice UNIQUE sobre ella. Este test verifica ambas piezas.
     */
    @Test
    void v5_cover_uniqueness_via_generated_column() {
        // 1. La columna generada cover_key existe (generation_expression no vacía)
        List<String> generatedCols = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'product_image' "
                        + "  AND generation_expression <> ''",
                String.class);

        assertThat(generatedCols).contains("cover_key");

        // 2. El índice uq_product_image_cover existe y es UNIQUE (non_unique = 0)
        Integer nonUnique = jdbcTemplate.queryForObject(
                "SELECT non_unique FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'product_image' "
                        + "  AND index_name = 'uq_product_image_cover' LIMIT 1",
                Integer.class);

        assertThat(nonUnique).isZero();
    }
}
