package pe.com.krypton.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.com.krypton.client.dto.ProductoResponse;
import pe.com.krypton.dto.response.CarritoResponse;
import pe.com.krypton.dto.response.ItemCarritoResponse;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;

/**
 * Mapper manual. El carrito NO guarda datos del producto: el nombre/sku/precio
 * llegan VIVOS desde catalog-service (ProductoResponse) y se inyectan acá.
 */
@Component
public class CarritoMapper {

    /** Línea enriquecida con el producto remoto. subtotal = precio vivo × cantidad. */
    public ItemCarritoResponse toItemResponse(ItemCarrito item, ProductoResponse product) {
        BigDecimal subtotal = product.price()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return new ItemCarritoResponse(
                item.getId(),
                product.id(),
                product.name(),
                product.sku(),
                product.price(),
                item.getQuantity(),
                subtotal);
    }

    /** Carrito + sus líneas ya resueltas. total = suma de subtotales. */
    public CarritoResponse toResponse(Carrito cart, List<ItemCarritoResponse> items) {
        BigDecimal total = items.stream()
                .map(ItemCarritoResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CarritoResponse(
                cart.getId(),
                items,
                total,
                cart.getUpdatedAt());
    }

    /** Carrito inexistente: respuesta vacía sin persistir nada. */
    public CarritoResponse emptyCart() {
        return new CarritoResponse(null, List.of(), BigDecimal.ZERO, null);
    }
}
