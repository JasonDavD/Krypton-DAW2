# Design: reportes — Exportable Admin Reports (Excel + PDF)

Grounded in real code under `pe.com.krypton`. Proposal #324, exploration #323. Approach B (locked). Hybrid artifact.

## Technical Approach

Service owns DATA, exporters own BYTES, controller orchestrates and shapes the binary HTTP response (SRP, each layer independently testable). `ReportService` (interface + impl) queries repositories and returns typed report DTOs. `ExcelExporter`/`PdfExporter` (`@Component`, one method per report) render a DTO to `byte[]`. `AdminReportController` exposes 8 endpoints under `/api/admin/reports` (path per format), already ADMIN-gated by `SecurityConfig` (`/api/admin/** → hasRole("ADMIN")`). 100% read-only, additive. No Flyway migration (ddl-auto `validate` untouched).

---

## 1. Package / Class Layout (under `backend/src/main/java/pe/com/krypton`)

| File | Action | Kind |
|------|--------|------|
| `service/ReportService.java` | Create | interface — 4 methods → typed DTOs |
| `service/impl/ReportServiceImpl.java` | Create | `@Service @Transactional(readOnly=true)`, ctor inject 4 repos |
| `report/ExcelExporter.java` | Create | `@Component` — 4 methods `byte[] exportX(dto)` (POI) |
| `report/PdfExporter.java` | Create | `@Component` — 4 methods `byte[] exportX(dto)` (OpenPDF) |
| `controller/AdminReportController.java` | Create | `@RestController @RequestMapping("/api/admin/reports")` |
| `dto/response/report/VentasPorPeriodoReport.java` | Create | record: wrapper |
| `dto/response/report/VentasPeriodoRow.java` | Create | record: row |
| `dto/response/report/TopProductosReport.java` | Create | record: wrapper |
| `dto/response/report/TopProductoRow.java` | Create | record: row |
| `dto/response/report/KardexReport.java` | Create | record: wrapper |
| `dto/response/report/KardexMovimientoRow.java` | Create | record: row |
| `dto/response/report/OrdenesListadoReport.java` | Create | record: wrapper (reuses `OrderResponse`) |
| `spec/OrderSpecification.java` | Create | static predicate factories (mirrors `ProductSpecification`) |
| `repository/OrderRepository.java` | Modify | add `JpaSpecificationExecutor<Order>` + native R1 queries |
| `repository/OrderItemRepository.java` | Modify | add JPQL R2 grouped query |
| `repository/StockMovementRepository.java` | Modify | add R3 derived query |
| `pom.xml` | Modify | add poi-ooxml 5.5.1 + openpdf 2.0.3 |

DTOs are **records** (project convention). Each report DTO **wraps a list + a summary header**:

```java
public record VentasPorPeriodoReport(
        Instant desde, Instant hasta, String granularidad,
        long totalOrdenes, BigDecimal totalFacturado, BigDecimal ticketPromedio,
        List<VentasPeriodoRow> filas) {}
public record VentasPeriodoRow(LocalDate periodo, long ordenes, BigDecimal monto) {}

public record TopProductosReport(Instant desde, Instant hasta, int limit, List<TopProductoRow> filas) {}
public record TopProductoRow(Long productId, String sku, String nombre, long unidades, BigDecimal ingresos) {}

public record KardexReport(Long productId, String sku, String nombre, int stockActual,
        Instant desde, Instant hasta, List<KardexMovimientoRow> movimientos) {}
public record KardexMovimientoRow(Instant fecha, String tipo, int cantidad, String reason, String reference) {}

public record OrdenesListadoReport(String statusFiltro, Instant desde, Instant hasta, Long userId,
        long total, List<OrderResponse> ordenes) {}
```

---

## 2. Repository Changes (additive) + Lima Timezone

**Lima-day → Instant boundary (computed in the service, NOT in SQL):** the controller binds `desde`/`hasta` as `LocalDate` (`@DateTimeFormat(iso=DATE)`). The service converts:
```java
ZoneId LIMA = ZoneId.of("America/Lima");
Instant start = desde.atStartOfDay(LIMA).toInstant();          // inclusive 00:00 Lima
Instant end   = hasta.plusDays(1).atStartOfDay(LIMA).toInstant(); // EXCLUSIVE next-day 00:00 Lima
```
So `WHERE order_date >= :start AND order_date < :end` is a half-open Instant range. Grouping label is bucketed in Lima time inside the native query via `AT TIME ZONE`.

### R1 — `OrderRepository` (native SQL, projection interface)
`:gran` is bound as a literal `'day'`/`'month'` chosen by the service from `granularidad`.

```java
public interface VentasPeriodoProjection {
    java.time.LocalDate getPeriodo();   // mapped from date column
    long getOrdenes();
    java.math.BigDecimal getMonto();
}
public interface VentasTotalesProjection {
    long getTotalOrdenes();
    java.math.BigDecimal getTotalFacturado();
    java.math.BigDecimal getTicketPromedio();
}

@Query(value = """
    SELECT date_trunc(:gran, o.order_date AT TIME ZONE 'America/Lima')::date AS periodo,
           COUNT(o.id) AS ordenes,
           COALESCE(SUM(o.total), 0) AS monto
    FROM orders o
    WHERE o.status = 'CONFIRMADA' AND o.order_date >= :start AND o.order_date < :end
    GROUP BY 1 ORDER BY 1
    """, nativeQuery = true)
List<VentasPeriodoProjection> ventasPorPeriodo(@Param("gran") String gran,
        @Param("start") Instant start, @Param("end") Instant end);

@Query(value = """
    SELECT COUNT(id) AS totalOrdenes,
           COALESCE(SUM(total), 0) AS totalFacturado,
           COALESCE(AVG(total), 0) AS ticketPromedio
    FROM orders WHERE status = 'CONFIRMADA' AND order_date >= :start AND order_date < :end
    """, nativeQuery = true)
VentasTotalesProjection ventasTotales(@Param("start") Instant start, @Param("end") Instant end);
```
`date_trunc(...)::date` returns a calendar date already shifted to Lima → maps cleanly to `LocalDate` (no further TZ ambiguity in Java). **Projection = Spring Data interface projection** (avoids brittle `Object[]` casting; constructor expressions are not allowed in native queries).

`OrderRepository` also `extends JpaSpecificationExecutor<Order>` for R4.

### R2 — `OrderItemRepository` (JPQL, top-N via `Pageable`)
```java
@Query("""
    SELECT new pe.com.krypton.dto.response.report.TopProductoRow(
        oi.product.id, oi.product.sku, oi.product.name,
        SUM(oi.quantity), SUM(oi.quantity * oi.unitPrice))
    FROM OrderItem oi JOIN oi.order o
    WHERE o.status = pe.com.krypton.model.enums.OrderStatus.CONFIRMADA
      AND o.orderDate >= :start AND o.orderDate < :end
    GROUP BY oi.product.id, oi.product.sku, oi.product.name
    ORDER BY SUM(oi.quantity) DESC""")
List<TopProductoRow> topProductos(@Param("start") Instant start,
        @Param("end") Instant end, Pageable pageable);
```
Constructor expression maps straight into `TopProductoRow` (record). `SUM(quantity)` returns `Long`, `SUM(quantity*unitPrice)` returns `BigDecimal` → record component types match. Top-N: `PageRequest.of(0, limit)`. JPQL is portable here (no `date_trunc`); date range still uses Lima-derived Instants.

### R3 — `StockMovementRepository` (derived query)
The product FK field on `StockMovement` is `product` (`@JoinColumn product_id`). Derived name traverses the relation id:
```java
List<StockMovement> findByProduct_IdAndCreatedAtBetweenOrderByCreatedAtAsc(
        Long productId, Instant start, Instant end);
List<StockMovement> findByProduct_IdOrderByCreatedAtAsc(Long productId); // when no date filter
```
Current stock: `ProductRepository.findById(productId)` → `Product.getStock()`; 404 if absent.

### R4 — `OrderSpecification` (NEW, mirrors `ProductSpecification`)
Null-predicate contract. `findAll(spec, Sort.by(orderDate).desc())` (export = unpaginated list).
```java
hasStatus(OrderStatus s)  → null if s==null   else cb.equal(root.get("status"), s)
dateBetween(Instant a, Instant b) → null if both null; ge/le/between on root.get("orderDate")
hasUser(Long userId)      → null if null      else cb.equal(root.get("user").get("id"), userId)
```
Items per order loaded via existing `orderItemRepository.findByOrder(o)` + `orderMapper.toResponse(o, items)` (N+1 accepted at academic scale, locked).

---

## 3. Exporter Mechanics

Shared private helpers per exporter to kill duplication while keeping one public method per report.

**`ExcelExporter` (POI `XSSFWorkbook`):** helper `CellStyle headerStyle(workbook)` (bold + grey fill); helper `write(workbook) → byte[]` via `ByteArrayOutputStream`, `workbook.write(baos)`, `workbook.close()`. Each method: create sheet → header row (styled) → summary rows → data rows → autosize → return bytes.

**`PdfExporter` (OpenPDF):** helper `byte[] render(Consumer<Document> body)` opening `Document` + `PdfWriter.getInstance(doc, baos)`, `doc.open()`, run body, `doc.close()`. Each method builds a `PdfPTable` with header cells + data rows + a title `Paragraph`.

Column layouts:
- **R1 Ventas:** title + summary (totalOrdenes, totalFacturado, ticketPromedio); table `Periodo | Órdenes | Monto`.
- **R2 Top productos:** `# | SKU | Producto | Unidades | Ingresos`.
- **R3 Kardex:** header block (SKU, nombre, stock actual); table `Fecha | Tipo | Cantidad | Motivo | Referencia`.
- **R4 Órdenes:** `#Orden | Fecha | Usuario | Estado | Total`.
- Empty list → still emit header row / title → valid empty document (200).

---

## 4. Controller — 8 methods

Pattern (per format pair); ADMIN enforced globally, no per-method annotation.
```java
@GetMapping(value = "/ventas/excel",
    produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
public ResponseEntity<byte[]> ventasExcel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        @RequestParam(defaultValue = "DAY") String granularidad) {
    var data = reportService.ventasPorPeriodo(desde, hasta, granularidad);
    return file(excelExporter.exportVentas(data), "ventas.xlsx", XLSX);
}
```
Helper `ResponseEntity<byte[]> file(byte[] body, String name, MediaType ct)`:
`ResponseEntity.ok().contentType(ct).header(CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"").body(body)`.

| Endpoint | Params (defaults) | Content-Type | filename |
|----------|-------------------|--------------|----------|
| `/ventas/{excel,pdf}` | `desde`,`hasta` (req), `granularidad`=DAY | xlsx / `application/pdf` | ventas.xlsx/pdf |
| `/productos-vendidos/{excel,pdf}` | `desde`,`hasta` (req), `limit`=10 (max 100) | … | productos_vendidos_.* |
| `/kardex/{excel,pdf}` | `productId` (req), `desde`,`hasta` (opt) | … | kardex-{id}.* |
| `/ordenes/{excel,pdf}` | `status`,`desde`,`hasta`,`userId` (all opt) | … | ordenes.* |

PDF Content-Type = `MediaType.APPLICATION_PDF`. Validation: missing required `@RequestParam` → Spring `MissingServletRequestParameterException`; bad date format → `MethodArgumentTypeMismatchException`; bad `status` enum / `limit` over max → service throws. **Add two handlers to existing `GlobalExceptionHandler` (`@RestControllerAdvice`)** returning `ApiError(400,...)` for those two exceptions (currently only `MethodArgumentNotValidException` is mapped). `limit>100` and unknown `status` → `IllegalArgumentException` handler → 400. 403 (CLIENTE) / 401 (no token) come free from `SecurityConfig`.

---

## 5. TDD Test Plan (bottom-up build order)

| Layer | Test class | Asserts |
|-------|-----------|---------|
| Repo (Testcontainers PG16, `extends AbstractIntegrationTest`) | `ReportRepositoryIntegrationTest` | seed CONFIRMADA + non-CONFIRMADA orders spanning a Lima midnight boundary; assert R1 buckets by Lima day, CANCELADA/PENDIENTE excluded, totals COUNT/SUM/AVG; R2 GROUP BY + DESC + top-N; R3 `findByProduct_Id...Between` window; R4 spec composition |
| Spec (unit) | `OrderSpecificationTest` | each factory returns null when arg null, builds predicate otherwise (mirrors `ProductSpecificationTest`) |
| Service (Mockito) | `ReportServiceImplTest` | mock 4 repos; assert Lima Instant boundary math (`start`/`end`), `gran` literal mapping DAY→'day' MONTH→'month', `PageRequest.of(0,limit)`, 404 on missing product (R3), DTO assembly + summary fields |
| Exporter (pure Java) | `ExcelExporterTest` | re-parse `byte[]` via `new XSSFWorkbook(new ByteArrayInputStream(bytes))`; assert header text + cell values + row count; empty DTO → header-only sheet |
| Exporter (pure Java) | `PdfExporterTest` | bytes non-null, start with `%PDF` magic, length > 0; empty DTO still valid |
| Controller (`@WebMvcTest`, `addFilters=false`, exclude `JwtAuthenticationFilter`) | `AdminReportControllerTest` | mock service + both exporters; per endpoint assert 200, exact `Content-Type`, `Content-Disposition: attachment`, body present; missing required param → 400; default params applied |
| E2E (optional, `extends AbstractIntegrationTest`) | `ReportIntegrationTest` | admin JWT round-trip → 200 + binary; CLIENTE → 403; no token → 401 (FK cleanup prefix pattern as `OrderIntegrationTest`) |

**Build order:** (1) pom deps → (2) DTO records → (3) repo queries + `OrderSpecification` + spec test + repo IT (RED→GREEN proves SQL/TZ) → (4) `ReportService` interface+impl + service unit test → (5) exporters + exporter unit tests → (6) controller + `GlobalExceptionHandler` additions + web-slice test → (7) optional E2E. Each step RED→GREEN→REFACTOR (`cd backend && ./mvnw test`).

---

## 6. Risks / Locked Decisions

- **TZ expression LOCKED:** `date_trunc(:gran, o.order_date AT TIME ZONE 'America/Lima')::date`; Java boundaries via `LocalDate.atStartOfDay(ZoneId.of("America/Lima"))`, end exclusive next-day. Repo IT crossing a Lima midnight is the proof.
- **Dependency conflicts:** add only 2 GAVs (`poi-ooxml:5.5.1`, `openpdf:2.0.3`); run `cd backend && ./mvnw dependency:tree` after adding to confirm no clash with Boot 3.3.5 BOM (POI pulls commons-compress/xmlbeans/log4j-api — all safe).
- **OpenPDF Java-17 pin:** stay on 2.0.x; 2.1+/3.x need Java 21. No version property — pin inline.
- **In-memory generation accepted** (`ByteArrayOutputStream`); `SXSSFWorkbook` streaming = future tech debt.
- **PDF assertion:** smoke (`%PDF` + non-empty); full cell assertions only on Excel.
- **R2 SUM types:** verify JPQL `SUM(quantity)`→`Long`, `SUM(quantity*unitPrice)`→`BigDecimal` align with `TopProductoRow` components; if Hibernate widens, adjust record types in tasks.

## Open Questions
- None blocking. (Confirm R2 SUM return types empirically during the repo test — adjust record component type if Hibernate returns `Long`/`Double` unexpectedly.)
