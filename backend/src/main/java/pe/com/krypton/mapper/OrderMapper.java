package pe.com.krypton.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.OrderItemResponse;
import pe.com.krypton.dto.response.OrderResponse;
import pe.com.krypton.model.Order;
import pe.com.krypton.model.OrderItem;

/** Manual mapper — mirrors CartMapper. Items are passed in explicitly (no @OneToMany on Order). */
@Component
public class OrderMapper {

    /**
     * Maps one OrderItem to its response DTO.
     * subtotal = unitPrice (snapshot) × quantity.
     * NEVER reads item.getProduct().getPrice() — that would break the price snapshot invariant.
     */
    public OrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal subtotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return new OrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                subtotal);
    }

    /**
     * Maps an Order + its pre-loaded items to the response DTO.
     * total  → order.getTotal()  (persisted snapshot — NOT recomputed from items).
     * status → enum.name() String (wire-decoupled from the Java enum).
     * userId → order.getUser().getId() (required for admin attribution — REQ-OM-10).
     */
    public OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderResponse(
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
