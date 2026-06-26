package pe.com.krypton.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Vista de la orden con sus líneas. status/documentType serializados como String. */
public record OrdenResponse(
        Long id,
        String userEmail,
        Instant orderDate,
        String status,
        String documentType,
        String customerName,
        String customerDoc,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal igv,
        BigDecimal total,
        List<ItemOrdenResponse> items) {
}
