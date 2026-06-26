package pe.com.krypton.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.ItemOrdenResponse;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.entity.Orden;
import pe.com.krypton.entity.ItemOrden;

/** Manual mapper — mirrors CarritoMapper. Items are passed in explicitly (no @OneToMany on Orden). */
@Component
public class OrdenMapper {

    /**
     * Maps one ItemOrden to its response DTO.
     * subtotal = unitPrice (snapshot) × quantity.
     * NEVER reads item.getProduct().getPrice() — that would break the price snapshot invariant.
     */
    public ItemOrdenResponse toItemResponse(ItemOrden item) {
        BigDecimal subtotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return new ItemOrdenResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                subtotal);
    }

    /**
     * Maps an Orden + its pre-loaded items to the response DTO.
     * total  → order.getTotal()  (persisted snapshot — NOT recomputed from items).
     * status → enum.name() String (wire-decoupled from the Java enum).
     * userId → order.getUser().getId() (required for admin attribution — REQ-OM-10).
     */
    public OrdenResponse toResponse(Orden order, List<ItemOrden> items) {
        List<ItemOrdenResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();
        return new OrdenResponse(
                order.getId(),
                order.getUser().getId(),
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
