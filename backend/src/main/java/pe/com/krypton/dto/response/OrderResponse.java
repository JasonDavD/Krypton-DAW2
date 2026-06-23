package pe.com.krypton.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Respuesta de pedido. Incluye el desglose del comprobante: el precio del catálogo
 * YA trae IGV incluido, por eso `igv` se desglosa hacia adentro del total
 * (base = total − igv). `total = subtotal + shippingCost`. Boleta y factura
 * devuelven el mismo total; sólo cambia si el front muestra el desglose.
 */
public record OrderResponse(
        Long id,
        Long userId,
        Instant orderDate,
        String status,
        String documentType,
        String customerName,
        String customerDoc,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal igv,
        BigDecimal total,
        List<OrderItemResponse> items) {
}
