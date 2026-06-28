package pe.com.krypton.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.entity.MovimientoStock;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.enums.TipoMovimiento;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.repository.MovimientoStockRepository;
import pe.com.krypton.repository.ProductoRepository;

/** Unit test del kardex (catalog es dueño de stock_movement). Mockea repos: NO toca DB. */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private MovimientoStockRepository movimientoRepository;

    @InjectMocks private ReportServiceImpl reportService;

    @Test
    void should_build_kardex_without_dates_when_product_exists() {
        Producto p = new Producto();
        p.setId(1L);
        p.setSku("KR-001");
        p.setName("Laptop");
        p.setStock(7);
        MovimientoStock m = new MovimientoStock();
        m.setType(TipoMovimiento.SALIDA);
        m.setQuantity(2);
        m.setReason("Salida por ORDER-1");
        m.setReference("ORDER-1");
        m.setCreatedAt(Instant.EPOCH);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));
        when(movimientoRepository.findByProduct_IdOrderByCreatedAtAsc(1L)).thenReturn(List.of(m));

        KardexReport report = reportService.kardex(1L, null, null);

        assertThat(report.productId()).isEqualTo(1L);
        assertThat(report.sku()).isEqualTo("KR-001");
        assertThat(report.stockActual()).isEqualTo(7);
        assertThat(report.movimientos()).singleElement().satisfies(r -> {
            assertThat(r.tipo()).isEqualTo("SALIDA");
            assertThat(r.cantidad()).isEqualTo(2);
            assertThat(r.reference()).isEqualTo("ORDER-1");
        });
    }

    @Test
    void should_throw_not_found_when_product_missing() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.kardex(99L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
