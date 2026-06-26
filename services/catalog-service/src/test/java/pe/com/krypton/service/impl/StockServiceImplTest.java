package pe.com.krypton.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.krypton.dto.request.StockItemRequest;
import pe.com.krypton.dto.request.StockMovementRequest;
import pe.com.krypton.entity.MovimientoStock;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.enums.TipoMovimiento;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.repository.MovimientoStockRepository;
import pe.com.krypton.repository.ProductoRepository;

/**
 * Unit test del lado proveedor de Feign: descontar stock + registrar SALIDA.
 * Mockea los repositories (no toca DB). Strict TDD: este test va PRIMERO (RED).
 */
@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private MovimientoStockRepository movimientoRepository;

    @InjectMocks
    private StockServiceImpl stockService;

    @Test
    void should_decrease_stock_and_record_salida_when_stock_is_enough() {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setStock(10);
        when(productoRepository.findByIdWithLock(1L)).thenReturn(Optional.of(producto));

        StockMovementRequest request = new StockMovementRequest(
                "ORDER-99", 7L, List.of(new StockItemRequest(1L, 3)));

        stockService.descontar(request);

        // 1) el stock cacheado bajó 10 → 7
        assertThat(producto.getStock()).isEqualTo(7);

        // 2) se registró UN movimiento SALIDA con los datos correctos
        ArgumentCaptor<MovimientoStock> captor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoRepository).save(captor.capture());
        MovimientoStock mov = captor.getValue();
        assertThat(mov.getType()).isEqualTo(TipoMovimiento.SALIDA);
        assertThat(mov.getQuantity()).isEqualTo(3);
        assertThat(mov.getReference()).isEqualTo("ORDER-99");
        assertThat(mov.getCreatedBy()).isEqualTo(7L);
        assertThat(mov.getProduct()).isSameAs(producto);
        assertThat(mov.getCreatedAt()).isNotNull();
    }

    @Test
    void should_throw_insufficient_stock_when_quantity_exceeds_stock() {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setStock(2);
        when(productoRepository.findByIdWithLock(1L)).thenReturn(Optional.of(producto));

        StockMovementRequest request = new StockMovementRequest(
                "ORDER-99", 7L, List.of(new StockItemRequest(1L, 5)));

        assertThatThrownBy(() -> stockService.descontar(request))
                .isInstanceOf(InsufficientStockException.class);

        // ni se descuenta ni se registra movimiento: todo o nada
        assertThat(producto.getStock()).isEqualTo(2);
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void should_increase_stock_and_record_entrada_when_restoring() {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setStock(5);
        when(productoRepository.findByIdWithLock(1L)).thenReturn(Optional.of(producto));

        StockMovementRequest request = new StockMovementRequest(
                "ORDER-99", 7L, List.of(new StockItemRequest(1L, 3)));

        stockService.restaurar(request);

        // 1) el stock cacheado subió 5 → 8 (compensación)
        assertThat(producto.getStock()).isEqualTo(8);

        // 2) se registró UN movimiento ENTRADA con los datos correctos
        ArgumentCaptor<MovimientoStock> captor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoRepository).save(captor.capture());
        MovimientoStock mov = captor.getValue();
        assertThat(mov.getType()).isEqualTo(TipoMovimiento.ENTRADA);
        assertThat(mov.getQuantity()).isEqualTo(3);
        assertThat(mov.getReference()).isEqualTo("ORDER-99");
        assertThat(mov.getProduct()).isSameAs(producto);
    }
}
