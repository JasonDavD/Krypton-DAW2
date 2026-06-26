package pe.com.krypton.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.krypton.client.CatalogClient;
import pe.com.krypton.client.dto.ProductoResponse;
import pe.com.krypton.client.dto.StockMovementRequest;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;
import pe.com.krypton.entity.Orden;
import pe.com.krypton.entity.enums.EstadoOrden;
import pe.com.krypton.entity.enums.TipoDocumento;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.mapper.OrdenMapper;
import pe.com.krypton.repository.CarritoRepository;
import pe.com.krypton.repository.ItemCarritoRepository;
import pe.com.krypton.repository.ItemOrdenRepository;
import pe.com.krypton.repository.OrdenRepository;

/**
 * Unit test de la SAGA del checkout distribuido (confirmarCompra). Mockea Feign (CatalogClient)
 * y los repos: NO toca DB ni red. Cubre los 3 comportamientos clave:
 *   1) happy path: crea orden + descuenta stock (Feign) + vacía carrito,
 *   2) stock insuficiente: 422 de catalog → InsufficientStockException, SIN compensar,
 *   3) COMPENSACIÓN: si falla al persistir DESPUÉS de descontar → restaura stock (Feign).
 */
@ExtendWith(MockitoExtension.class)
class OrdenServiceImplTest {

    @Mock private OrdenRepository ordenRepository;
    @Mock private ItemOrdenRepository itemOrdenRepository;
    @Mock private CarritoRepository carritoRepository;
    @Mock private ItemCarritoRepository itemCarritoRepository;
    @Mock private OrdenMapper ordenMapper;
    @Mock private CatalogClient catalogClient;

    @InjectMocks private OrdenServiceImpl ordenService;

    private static final String EMAIL = "cliente@krypton.pe";

    private Carrito cartConUnItem() {
        Carrito cart = new Carrito();
        cart.setId(7L);
        cart.setUserEmail(EMAIL);
        ItemCarrito item = new ItemCarrito();
        item.setId(3L);
        item.setCart(cart);
        item.setProductId(1L);
        item.setQuantity(2);
        when(carritoRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(cart));
        when(itemCarritoRepository.findByCart(cart)).thenReturn(List.of(item));
        when(catalogClient.getProduct(1L))
                .thenReturn(new ProductoResponse(1L, "KR-001", "Producto 1", new BigDecimal("100.00"), 10, true));
        return cart;
    }

    private static CheckoutRequest boletaRequest() {
        return new CheckoutRequest(TipoDocumento.BOLETA, "Juan Perez", "12345678");
    }

    @Test
    void should_create_order_decrease_stock_and_clear_cart_when_checkout_succeeds() {
        Carrito cart = cartConUnItem();
        when(ordenRepository.save(any(Orden.class))).thenAnswer(inv -> {
            Orden o = inv.getArgument(0);
            o.setId(50L);
            return o;
        });

        ordenService.confirmarCompra(EMAIL, boletaRequest());

        // 1) la orden se crea PENDIENTE con los montos calculados (subtotal 200, envío 20 < S/300, total 220)
        ArgumentCaptor<Orden> orderCap = ArgumentCaptor.forClass(Orden.class);
        verify(ordenRepository).save(orderCap.capture());
        Orden saved = orderCap.getValue();
        assertThat(saved.getStatus()).isEqualTo(EstadoOrden.PENDIENTE);
        assertThat(saved.getUserEmail()).isEqualTo(EMAIL);
        assertThat(saved.getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(saved.getShippingCost()).isEqualByComparingTo("20.00");
        assertThat(saved.getTotal()).isEqualByComparingTo("220.00");

        // 2) se descuenta stock en catalog con referencia ORDER-50 (el id de la orden)
        ArgumentCaptor<StockMovementRequest> stockCap = ArgumentCaptor.forClass(StockMovementRequest.class);
        verify(catalogClient).decreaseStock(stockCap.capture());
        assertThat(stockCap.getValue().reference()).isEqualTo("ORDER-50");
        assertThat(stockCap.getValue().items()).singleElement()
                .satisfies(it -> {
                    assertThat(it.productId()).isEqualTo(1L);
                    assertThat(it.quantity()).isEqualTo(2);
                });

        // 3) se vacía el carrito y NO se compensa (todo salió bien)
        verify(itemCarritoRepository).deleteByCart(cart);
        verify(catalogClient, never()).restoreStock(any());
    }

    @Test
    void should_throw_insufficient_stock_and_not_compensate_when_catalog_returns_422() {
        cartConUnItem();
        when(ordenRepository.save(any(Orden.class))).thenAnswer(inv -> {
            Orden o = inv.getArgument(0);
            o.setId(51L);
            return o;
        });
        FeignException unprocessable = mock(FeignException.class);
        when(unprocessable.status()).thenReturn(422);
        doThrow(unprocessable).when(catalogClient).decreaseStock(any());

        assertThatThrownBy(() -> ordenService.confirmarCompra(EMAIL, boletaRequest()))
                .isInstanceOf(InsufficientStockException.class);

        // el descuento falló (catalog no commiteó) → NO hay nada que compensar
        verify(catalogClient, never()).restoreStock(any());
    }

    @Test
    void should_compensate_restore_stock_when_persisting_order_fails_after_decrease() {
        cartConUnItem();
        when(ordenRepository.save(any(Orden.class))).thenAnswer(inv -> {
            Orden o = inv.getArgument(0);
            o.setId(52L);
            return o;
        });
        // el descuento (Feign) sale OK, pero al persistir las líneas explota → debe compensar
        when(itemOrdenRepository.save(any())).thenThrow(new RuntimeException("DB caída"));

        assertThatThrownBy(() -> ordenService.confirmarCompra(EMAIL, boletaRequest()))
                .isInstanceOf(RuntimeException.class);

        // COMPENSACIÓN: se restaura el stock descontado (misma referencia)
        ArgumentCaptor<StockMovementRequest> restoreCap = ArgumentCaptor.forClass(StockMovementRequest.class);
        verify(catalogClient).restoreStock(restoreCap.capture());
        assertThat(restoreCap.getValue().reference()).isEqualTo("ORDER-52");
    }
}
