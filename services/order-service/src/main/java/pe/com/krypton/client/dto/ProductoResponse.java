package pe.com.krypton.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Vista PARCIAL del producto que devuelve catalog-service en GET /api/products/{id}.
 * Solo mapeamos lo que order necesita (precio, stock, estado); {@code ignoreUnknown}
 * descarta el resto de campos del JSON de catalog (description, imageUrl, category...).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoResponse(
        Long id,
        String sku,
        String name,
        BigDecimal price,
        int stock,
        boolean active) {
}
