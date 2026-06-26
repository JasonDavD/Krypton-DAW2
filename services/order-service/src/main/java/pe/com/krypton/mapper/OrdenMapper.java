package pe.com.krypton.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.ItemOrdenResponse;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.entity.ItemOrden;
import pe.com.krypton.entity.Orden;

/** Mapper manual. La orden es histórica: usa el SNAPSHOT (nombre + precio congelados). */
@Component
public class OrdenMapper {

    /**
     * Mapea una línea de la orden a su DTO.
     * subtotal = unitPrice (snapshot) × quantity; productName también es snapshot.
     * NUNCA llama a catalog: la orden es histórica y se lee sola.
     */
    public ItemOrdenResponse toItemResponse(ItemOrden item) {
        BigDecimal subtotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return new ItemOrdenResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                subtotal);
    }

    /** Mapea la orden + sus líneas pre-cargadas. status/documentType como String. */
    public OrdenResponse toResponse(Orden order, List<ItemOrden> items) {
        List<ItemOrdenResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();
        return new OrdenResponse(
                order.getId(),
                order.getUserEmail(),
                order.getOrderDate(),
                order.getStatus().name(),
                order.getDocumentType().name(),
                order.getCustomerName(),
                order.getCustomerDoc(),
                order.getSubtotal(),
                order.getShippingCost(),
                order.getIgv(),
                order.getTotal(),
                itemResponses);
    }
}
