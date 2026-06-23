package pe.com.krypton.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.response.OrderResponse;
import pe.com.krypton.dto.response.report.KardexMovimientoRow;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.OrdenesListadoReport;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPeriodoRow;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.OrderMapper;
import pe.com.krypton.model.Order;
import pe.com.krypton.model.Product;
import pe.com.krypton.model.StockMovement;
import pe.com.krypton.model.enums.OrderStatus;
import pe.com.krypton.repository.OrderItemRepository;
import pe.com.krypton.repository.OrderRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.StockMovementRepository;
import pe.com.krypton.repository.VentasTotalesProjection;
import pe.com.krypton.service.ReportService;
import pe.com.krypton.spec.OrderSpecification;

@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;

    public ReportServiceImpl(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             StockMovementRepository stockMovementRepository,
                             ProductRepository productRepository,
                             OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
    }

    // ─── R1: Ventas por período ──────────────────────────────────────────────────

    @Override
    public VentasPorPeriodoReport ventasPorPeriodo(LocalDate desde, LocalDate hasta, String granularidad) {
        validateDateRange(desde, hasta, true /* both required */);

        String gran = mapGranularidad(granularidad);

        Instant start = toStartOfDay(desde);
        Instant end   = toExclusiveEnd(hasta);

        var filas = orderRepository.ventasPorPeriodo(gran, start, end).stream()
                .map(p -> new VentasPeriodoRow(p.getPeriodo(), p.getOrdenes(), p.getMonto()))
                .toList();

        VentasTotalesProjection totales = orderRepository.ventasTotales(start, end);

        return new VentasPorPeriodoReport(
                start,
                end,
                granularidad,
                totales.getTotalOrdenes(),
                totales.getTotalFacturado(),
                totales.getTicketPromedio(),
                filas);
    }

    // ─── R2: Productos más vendidos ──────────────────────────────────────────────

    @Override
    public pe.com.krypton.dto.response.report.TopProductosReport topProductos(
            LocalDate desde, LocalDate hasta, int limit) {

        validateLimit(limit);
        validatePartialDateRange(desde, hasta);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("desde debe ser <= hasta");
        }

        var pageable = PageRequest.of(0, limit);

        var productos = (desde == null)
                ? orderItemRepository.findTopProductosSinFechas(pageable)
                : orderItemRepository.findTopProductos(toStartOfDay(desde), toExclusiveEnd(hasta), pageable);

        Instant startInst = desde == null ? null : toStartOfDay(desde);
        Instant endInst   = hasta == null ? null : toExclusiveEnd(hasta);

        return new TopProductosReport(startInst, endInst, limit, productos);
    }

    // ─── R3: Kardex por producto ─────────────────────────────────────────────────

    @Override
    public KardexReport kardexProducto(Long productId, LocalDate desde, LocalDate hasta) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Producto no encontrado: " + productId));

        validatePartialDateRange(desde, hasta);

        List<StockMovement> movements;
        Instant startInst = null;
        Instant endInst   = null;

        if (desde != null) {
            startInst = toStartOfDay(desde);
            endInst   = toExclusiveEnd(hasta);
            movements = stockMovementRepository
                    .findByProduct_IdAndCreatedAtBetweenOrderByCreatedAtAsc(productId, startInst, endInst);
        } else {
            movements = stockMovementRepository
                    .findByProduct_IdOrderByCreatedAtAsc(productId);
        }

        List<KardexMovimientoRow> rows = movements.stream()
                .map(sm -> new KardexMovimientoRow(
                        sm.getCreatedAt(),
                        sm.getType().name(),
                        sm.getQuantity(),
                        sm.getReason(),
                        sm.getReference()))
                .toList();

        return new KardexReport(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getStock(),
                startInst,
                endInst,
                rows);
    }

    // ─── R4: Listado de órdenes ──────────────────────────────────────────────────

    @Override
    public OrdenesListadoReport listadoOrdenes(String status, LocalDate desde, LocalDate hasta, Long userId) {
        validatePartialDateRange(desde, hasta);

        OrderStatus orderStatus = parseStatus(status);

        Instant startInst = desde != null ? toStartOfDay(desde) : null;
        Instant endInst   = hasta != null ? toExclusiveEnd(hasta) : null;

        Specification<Order> spec = Specification
                .where(OrderSpecification.hasStatus(orderStatus))
                .and(OrderSpecification.dateBetween(startInst, endInst))
                .and(OrderSpecification.hasUser(userId));

        List<Order> orders = orderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "orderDate"));

        List<OrderResponse> responses = orders.stream()
                .map(o -> orderMapper.toResponse(o, orderItemRepository.findByOrder(o)))
                .toList();

        return new OrdenesListadoReport(
                status,
                startInst,
                endInst,
                userId,
                responses.size(),
                responses);
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    /** Lima-timezone start-of-day → UTC Instant (inclusive lower bound). */
    private Instant toStartOfDay(LocalDate date) {
        return date.atStartOfDay(LIMA).toInstant();
    }

    /**
     * Lima-timezone start of NEXT day → UTC Instant (exclusive upper bound).
     * The range contract is half-open [start, end).
     */
    private Instant toExclusiveEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay(LIMA).toInstant();
    }

    /**
     * Maps the user-facing granularidad to the bucket token used by ventasPorPeriodo.
     * "dia" → "day", "mes" → "month". Null / unknown → IllegalArgumentException.
     */
    private String mapGranularidad(String granularidad) {
        if (granularidad == null) {
            throw new IllegalArgumentException("granularidad es obligatorio (dia|mes)");
        }
        return switch (granularidad.toLowerCase()) {
            case "dia"  -> "day";
            case "mes"  -> "month";
            default     -> throw new IllegalArgumentException(
                    "granularidad inválida: '" + granularidad + "'. Valores válidos: dia, mes");
        };
    }

    /**
     * Validates that limit is in range [1, 100].
     */
    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit debe ser >= 1, recibido: " + limit);
        }
        if (limit > 100) {
            throw new IllegalArgumentException("limit máximo es 100, recibido: " + limit);
        }
    }

    /**
     * Validates that BOTH dates are present or BOTH are absent.
     * Throws IllegalArgumentException if only one is provided (partial range).
     */
    private void validatePartialDateRange(LocalDate desde, LocalDate hasta) {
        if ((desde == null) != (hasta == null)) {
            throw new IllegalArgumentException(
                    "desde y hasta deben proporcionarse juntos (ambos o ninguno)");
        }
    }

    /**
     * Validates a required date range (both must be present) and desde <= hasta.
     */
    private void validateDateRange(LocalDate desde, LocalDate hasta, boolean required) {
        if (required && (desde == null || hasta == null)) {
            throw new IllegalArgumentException("desde y hasta son obligatorios");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("desde debe ser <= hasta");
        }
    }

    /**
     * Parses a status string (case-insensitive) to OrderStatus enum.
     * Returns null when input is null (no filter). Throws IllegalArgumentException for unknown values.
     */
    private OrderStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(OrderStatus.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "status inválido: '" + status + "'. Valores válidos: " + valid);
        }
    }
}
