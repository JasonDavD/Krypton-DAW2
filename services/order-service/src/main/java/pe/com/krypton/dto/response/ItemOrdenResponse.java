package pe.com.krypton.dto.response;

import java.math.BigDecimal;

/** Línea de la orden (snapshot histórico: nombre y precio congelados al comprar). */
public record ItemOrdenResponse(
        Long id,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
