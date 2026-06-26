package pe.com.krypton.dto.response;

import java.math.BigDecimal;

/** Línea del carrito enriquecida con datos VIVOS del producto (vía catalog-service). */
public record ItemCarritoResponse(
        Long itemId,
        Long productId,
        String productName,
        String sku,
        BigDecimal price,
        int quantity,
        BigDecimal subtotal) {
}
