package pe.com.krypton.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CarritoResponse(
        Long cartId,
        List<ItemCarritoResponse> items,
        BigDecimal total,
        Instant updatedAt) {
}
