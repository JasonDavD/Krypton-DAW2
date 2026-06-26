package pe.com.krypton.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Vista PARCIAL del producto que devuelve catalog-service en GET /api/products/{id}.
 * review solo necesita saber que el producto EXISTE; {@code ignoreUnknown} descarta el
 * resto de campos del JSON de catalog (description, price, stock, imageUrl, category...).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoResponse(
        Long id,
        String name,
        boolean active) {
}
