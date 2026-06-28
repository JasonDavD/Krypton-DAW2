package pe.com.krypton.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.client.CatalogClient;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.TopProductoRow;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPeriodoRow;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;
import pe.com.krypton.dto.response.report.VentasTotalesProjection;
import pe.com.krypton.repository.ItemOrdenRepository;
import pe.com.krypton.repository.OrdenRepository;
import pe.com.krypton.service.ReportService;

/**
 * Reportes locales de order-service. Las fechas (LocalDate del cliente) se interpretan en zona
 * America/Lima y se convierten a un rango half-open [start, end) en UTC para las queries.
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final OrdenRepository ordenRepository;
    private final ItemOrdenRepository itemOrdenRepository;
    private final CatalogClient catalogClient;

    public ReportServiceImpl(OrdenRepository ordenRepository, ItemOrdenRepository itemOrdenRepository,
                             CatalogClient catalogClient) {
        this.ordenRepository = ordenRepository;
        this.itemOrdenRepository = itemOrdenRepository;
        this.catalogClient = catalogClient;
    }

    @Override
    @Transactional(readOnly = true)
    public VentasPorPeriodoReport ventasPorPeriodo(LocalDate desde, LocalDate hasta, String granularidad) {
        validateDateRange(desde, hasta, true);
        String gran = mapGranularidad(granularidad);
        Instant start = toStartOfDay(desde);
        Instant end = toExclusiveEnd(hasta);

        List<VentasPeriodoRow> filas = ordenRepository.ventasPorPeriodo(gran, start, end).stream()
                .map(p -> new VentasPeriodoRow(p.getPeriodo(), p.getOrdenes(), p.getMonto()))
                .toList();

        VentasTotalesProjection totales = ordenRepository.ventasTotales(start, end);
        return new VentasPorPeriodoReport(
                start, end, granularidad,
                totales.getTotalOrdenes(),
                totales.getTotalFacturado(),
                totales.getTicketPromedio(),
                filas);
    }

    @Override
    @Transactional(readOnly = true)
    public TopProductosReport topProductos(LocalDate desde, LocalDate hasta, int limit) {
        validateLimit(limit);
        validatePartialDateRange(desde, hasta);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("desde debe ser <= hasta");
        }

        Pageable pageable = PageRequest.of(0, limit);
        List<Object[]> rows = (desde == null)
                ? itemOrdenRepository.findTopProductosSinFechas(pageable)
                : itemOrdenRepository.findTopProductos(toStartOfDay(desde), toExclusiveEnd(hasta), pageable);

        List<TopProductoRow> productos = rows.stream()
                .map(r -> new TopProductoRow(
                        (Long) r[0],
                        "",                              // snapshot sin sku (ver TopProductoRow)
                        (String) r[1],
                        ((Number) r[2]).longValue(),     // MySQL puede devolver BigDecimal
                        (BigDecimal) r[3]))
                .toList();

        Instant startInst = desde == null ? null : toStartOfDay(desde);
        Instant endInst = hasta == null ? null : toExclusiveEnd(hasta);
        return new TopProductosReport(startInst, endInst, limit, productos);
    }

    @Override
    public KardexReport kardexProducto(Long productId, LocalDate desde, LocalDate hasta) {
        validatePartialDateRange(desde, hasta);
        Instant start = desde == null ? null : toStartOfDay(desde);
        Instant end = hasta == null ? null : toExclusiveEnd(hasta);
        // El kardex vive en catalog-service (dueño de stock_movement): se pide por Feign.
        return catalogClient.kardex(productId, start, end);
    }

    // ── helpers ──

    private Instant toStartOfDay(LocalDate date) {
        return date.atStartOfDay(LIMA).toInstant();
    }

    private Instant toExclusiveEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay(LIMA).toInstant();
    }

    private String mapGranularidad(String granularidad) {
        if (granularidad == null) {
            throw new IllegalArgumentException("granularidad es obligatorio (dia|mes)");
        }
        return switch (granularidad.toLowerCase()) {
            case "dia" -> "day";
            case "mes" -> "month";
            default -> throw new IllegalArgumentException(
                    "granularidad inválida: '" + granularidad + "'. Valores válidos: dia, mes");
        };
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit debe ser >= 1, recibido: " + limit);
        }
        if (limit > 100) {
            throw new IllegalArgumentException("limit máximo es 100, recibido: " + limit);
        }
    }

    private void validatePartialDateRange(LocalDate desde, LocalDate hasta) {
        if ((desde == null) != (hasta == null)) {
            throw new IllegalArgumentException("desde y hasta deben proporcionarse juntos (ambos o ninguno)");
        }
    }

    private void validateDateRange(LocalDate desde, LocalDate hasta, boolean required) {
        if (required && (desde == null || hasta == null)) {
            throw new IllegalArgumentException("desde y hasta son obligatorios");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("desde debe ser <= hasta");
        }
    }
}
