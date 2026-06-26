package pe.com.krypton.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.CartItemResponse;
import pe.com.krypton.dto.response.CartResponse;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;
import pe.com.krypton.entity.Producto;

/** Manual mapper — no MapStruct, no @OneToMany on Carrito. Items are passed in explicitly. */
@Component
public class CarritoMapper {

    public CartItemResponse toItemResponse(ItemCarrito item) {
        Producto product = item.getProduct();
        BigDecimal subtotal = product.getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getPrice(),
                item.getQuantity(),
                subtotal);
    }

    public CartResponse toResponse(Carrito cart, List<ItemCarrito> items) {
        List<CartItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();
        BigDecimal total = itemResponses.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(
                cart.getId(),
                itemResponses,
                total,
                cart.getUpdatedAt());
    }

    public CartResponse emptyCart() {
        return new CartResponse(null, List.of(), BigDecimal.ZERO, null);
    }
}
