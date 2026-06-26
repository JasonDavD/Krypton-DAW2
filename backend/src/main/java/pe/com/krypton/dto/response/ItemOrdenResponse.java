package pe.com.krypton.dto.response;

import java.math.BigDecimal;

public record ItemOrdenResponse(
        Long id,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
