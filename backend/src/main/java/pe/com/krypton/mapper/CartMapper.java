package pe.com.krypton.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.CartItemResponse;
import pe.com.krypton.dto.response.CartResponse;
import pe.com.krypton.model.Cart;
import pe.com.krypton.model.CartItem;
import pe.com.krypton.model.Product;

/** Manual mapper — no MapStruct, no @OneToMany on Cart. Items are passed in explicitly. */
@Component
public class CartMapper {

    public CartItemResponse toItemResponse(CartItem item) {
        Product product = item.getProduct();
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

    public CartResponse toResponse(Cart cart, List<CartItem> items) {
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
