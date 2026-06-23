package pe.com.krypton.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
        Long cartId,
        List<CartItemResponse> items,
        BigDecimal total,
        Instant updatedAt) {
}
