package pe.com.krypton.service.impl;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.response.report.KardexMovimientoRow;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.entity.MovimientoStock;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.repository.MovimientoStockRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.service.ReportService;

@Service
public class ReportServiceImpl implements ReportService {

    private final ProductoRepository productoRepository;
    private final MovimientoStockRepository movimientoRepository;

    public ReportServiceImpl(ProductoRepository productoRepository,
                             MovimientoStockRepository movimientoRepository) {
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public KardexReport kardex(Long productId, Instant start, Instant end) {
        Producto producto = productoRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + productId));

        List<MovimientoStock> movimientos = (start != null && end != null)
                ? movimientoRepository.findByProduct_IdAndCreatedAtBetweenOrderByCreatedAtAsc(productId, start, end)
                : movimientoRepository.findByProduct_IdOrderByCreatedAtAsc(productId);

        List<KardexMovimientoRow> rows = movimientos.stream()
                .map(sm -> new KardexMovimientoRow(
                        sm.getCreatedAt(),
                        sm.getType().name(),
                        sm.getQuantity(),
                        sm.getReason(),
                        sm.getReference()))
                .toList();

        return new KardexReport(
                producto.getId(),
                producto.getSku(),
                producto.getName(),
                producto.getStock(),
                start,
                end,
                rows);
    }
}
