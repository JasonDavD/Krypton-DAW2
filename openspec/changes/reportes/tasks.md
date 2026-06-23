# Tasks: reportes — Exportable Admin Reports (Excel + PDF)

Artifact store: hybrid. Build order: bottom-up, Strict TDD (RED → GREEN per phase, `cd backend && ./mvnw test` gates each phase).

---

## Phase 1: Dependencies (pom.xml)

- [x] P1.1 Add `poi-ooxml:5.5.1` and `openpdf:2.0.3` (pinned, no version property) to `backend/pom.xml` `<dependencies>` block.
- [x] P1.2 Run `cd backend && ./mvnw dependency:tree` and confirm no Boot 3.3.5 BOM conflict with `commons-compress`, `xmlbeans`, `log4j-api`; record output in PR description.
- [x] P1.3 Run `cd backend && ./mvnw test` — all pre-existing tests GREEN before any new code.

---

## Phase 2: DTO Records

- [x] P2.1 Create `backend/src/main/java/pe/com/krypton/dto/response/report/VentasPeriodoRow.java` — record `(LocalDate periodo, long ordenes, BigDecimal monto)`. [REQ-RPT-01]
- [x] P2.2 Create `backend/src/main/java/pe/com/krypton/dto/response/report/VentasPorPeriodoReport.java` — record `(Instant desde, Instant hasta, String granularidad, long totalOrdenes, BigDecimal totalFacturado, BigDecimal ticketPromedio, List<VentasPeriodoRow> filas)`. [REQ-RPT-01]
- [x] P2.3 Create `backend/src/main/java/pe/com/krypton/dto/response/report/TopProductoRow.java` — record `(Long productId, String sku, String nombre, long unidades, BigDecimal ingresos)`. [REQ-RPT-02] NOTE: types confirmed pending Docker/real-Postgres run; P3.3 test asserts at runtime.
- [x] P2.4 Create `backend/src/main/java/pe/com/krypton/dto/response/report/TopProductosReport.java` — record `(Instant desde, Instant hasta, int limit, List<TopProductoRow> productos)`. [REQ-RPT-02]
- [x] P2.5 Create `backend/src/main/java/pe/com/krypton/dto/response/report/KardexMovimientoRow.java` — record `(Instant fecha, String tipo, int cantidad, String reason, String reference)`. [REQ-RPT-03]
- [x] P2.6 Create `backend/src/main/java/pe/com/krypton/dto/response/report/KardexReport.java` — record `(Long productId, String sku, String nombre, int stockActual, Instant desde, Instant hasta, List<KardexMovimientoRow> movimientos)`. [REQ-RPT-03]
- [x] P2.7 Create `backend/src/main/java/pe/com/krypton/dto/response/report/OrdenesListadoReport.java` — record `(String statusFiltro, Instant desde, Instant hasta, Long userId, long total, List<OrderResponse> ordenes)`. [REQ-RPT-04]
- [x] P2.8 Run `cd backend && ./mvnw test` — compilation clean, all non-Docker tests GREEN.

---

## Phase 3: Repository Layer + OrderSpecification (RED → GREEN)

> Load-bearing correctness gate: native SQL, Lima TZ, and R2 SUM type alignment proven here.

- [x] P3.1 **[RED]** Create `backend/src/test/java/pe/com/krypton/spec/OrderSpecificationTest.java` — unit tests (no Spring context) verifying: `hasStatus(null)` → null, `hasStatus(CONFIRMADA)` → non-null predicate; `dateBetween(null,null)` → null, one bound → ge/le, both → between; `hasUser(null)` → null, `hasUser(1L)` → non-null. Mirrors `ProductSpecificationTest`. RED confirmed: symbol not found.
- [x] P3.2 **[GREEN]** Create `backend/src/main/java/pe/com/krypton/spec/OrderSpecification.java` — static null-predicate factories: `hasStatus(OrderStatus)`, `dateBetween(Instant,Instant)`, `hasUser(Long)`. Mirrors `ProductSpecification` pattern. [REQ-RPT-04] GREEN: 8/8 tests pass.
- [x] P3.3 **[RED]** Create `backend/src/test/java/pe/com/krypton/ReportRepositoryIntegrationTest.java` extending `AbstractIntegrationTest`. Compiles clean. BLOCKED: Docker daemon not running — `AbstractIntegrationTest` fails with `ExceptionInInitializerError` (same pre-existing condition as all other IT tests). Will be GREEN when Docker starts.
- [x] P3.4 Add `VentasPeriodoProjection` interface (top-level in `repository/`) with `LocalDate getPeriodo()`, `long getOrdenes()`, `BigDecimal getMonto()`. Required by R1 native query return type.
- [x] P3.5 Add `VentasTotalesProjection` interface with `long getTotalOrdenes()`, `BigDecimal getTotalFacturado()`, `BigDecimal getTicketPromedio()`. Required by R1 totals query.
- [x] P3.6 **[GREEN]** Modify `backend/src/main/java/pe/com/krypton/repository/OrderRepository.java` — add `extends JpaSpecificationExecutor<Order>`; add native `ventasPorPeriodo`; add native `ventasTotales`. [REQ-RPT-01, REQ-RPT-04]
- [x] P3.7 **[GREEN]** Modify `backend/src/main/java/pe/com/krypton/repository/OrderItemRepository.java` — add JPQL constructor-expression `findTopProductos` + `findTopProductosSinFechas` with `Pageable`. [REQ-RPT-02]
- [x] P3.8 **[GREEN]** Modify `backend/src/main/java/pe/com/krypton/repository/StockMovementRepository.java` — add `findByProduct_IdAndCreatedAtBetweenOrderByCreatedAtAsc` and `findByProduct_IdOrderByCreatedAtAsc`. [REQ-RPT-03]
- [x] P3.9 Run `cd backend && ./mvnw test` — ALL 243 tests GREEN (0 failures, 0 errors, 0 skipped). `ReportRepositoryIntegrationTest` 11/11 GREEN. R2 type resolution: Hibernate returns `Long` (boxed) for `SUM(quantity)` → `TopProductoRow.unidades` changed to `Long`. `BigDecimal` for `SUM(quantity*unitPrice)` confirmed correct. `:gran` bound param (`date_trunc(:gran, ...)`) ACCEPTED by Postgres JDBC without fallback needed.

---

## Phase 4: ReportService (RED → GREEN)

- [x] P4.1 **[RED]** Create `backend/src/test/java/pe/com/krypton/service/ReportServiceImplTest.java` — Mockito unit tests (no Spring context). Cover: (a) Lima Instant boundary math: `desde=2024-03-01` → start=`2024-03-01T05:00:00Z`, end=`2024-03-02T05:00:00Z` (UTC exclusive next-day); (b) `granularidad="dia"` → repo called with `"day"`, `granularidad="mes"` → `"month"`, invalid granularidad → `IllegalArgumentException`; (c) `ventasPorPeriodo`: desde>hasta → `IllegalArgumentException`; (d) `topProductos`: limit>100 → `IllegalArgumentException`, limit<=0 → `IllegalArgumentException`, partial date range → `IllegalArgumentException`; (e) `kardexProducto`: product not found → `ResourceNotFoundException` (404); (f) `listadoOrdenes`: invalid status string → `IllegalArgumentException`; (g) `listadoOrdenes`: partial date range → `IllegalArgumentException`. [REQ-RPT-01..04, REQ-RPT-07]
- [x] P4.2 Create `backend/src/main/java/pe/com/krypton/service/ReportService.java` — interface with 4 methods: `ventasPorPeriodo(LocalDate,LocalDate,String)→VentasPorPeriodoReport`; `topProductos(LocalDate,LocalDate,int)→TopProductosReport`; `kardexProducto(Long,LocalDate,LocalDate)→KardexReport`; `listadoOrdenes(String,LocalDate,LocalDate,Long)→OrdenesListadoReport`. [REQ-RPT-01..04]
- [x] P4.3 **[GREEN]** Create `backend/src/main/java/pe/com/krypton/service/impl/ReportServiceImpl.java` — `@Service @Transactional(readOnly=true)`. Constructor injects `OrderRepository`, `OrderItemRepository`, `StockMovementRepository`, `ProductRepository`. Implements: Lima-boundary math (`ZoneId.of("America/Lima")`, end=`hasta.plusDays(1).atStartOfDay(LIMA).toInstant()` exclusive); granularidad mapping `"dia"→"day"/"mes"→"month"`, default `"dia"`; all validation (dates, enums, limits); assembles DTOs from projection/query results. [REQ-RPT-01..04, REQ-RPT-07]
- [x] P4.4 Run `cd backend && ./mvnw test` — 267 tests GREEN (24 new from ReportServiceImplTest). BUILD SUCCESS.

---

## Phase 5: Exporters (RED → GREEN)

- [x] P5.1 **[RED]** Create `backend/src/test/java/pe/com/krypton/report/ExcelExporterTest.java` — pure Java unit tests (no Spring). For each of 4 methods: (a) non-empty DTO → re-parse bytes via `new XSSFWorkbook(new ByteArrayInputStream(bytes))`, assert column-header row with >= 3 cells + data row count; (b) empty list DTO → valid `XSSFWorkbook` (no exception), rows exist. [REQ-RPT-05] Note: `XSSFRow` is `Iterable<Cell>` so used `assertNotNull` (JUnit 5) to avoid AssertJ ambiguity; empty-case assertion is `>= 1` (summary rows + header) not strictly 1.
- [x] P5.2 **[RED]** Create `backend/src/test/java/pe/com/krypton/report/PdfExporterTest.java` — pure Java unit tests. For each of 4 methods: (a) bytes non-null + length > 0 + starts with `%PDF` magic (0x25 0x50 0x44 0x46); (b) empty list DTO → still valid `%PDF` (no exception). [REQ-RPT-05]
- [x] P5.3 **[GREEN]** Create `backend/src/main/java/pe/com/krypton/report/ExcelExporter.java` — `@Component`. Private helpers: `headerStyle(XSSFWorkbook)` (bold + grey fill), `toBytes(XSSFWorkbook)` (ByteArrayOutputStream + close). Public methods: `exportVentas`, `exportTopProductos`, `exportKardex`, `exportOrdenes` each `→byte[]`. Summary rows precede column-header rows. TopProducto: unbox `Long` via `.longValue()`. Empty list → summary + header rows only → valid binary. [REQ-RPT-05]
- [x] P5.4 **[GREEN]** Create `backend/src/main/java/pe/com/krypton/report/PdfExporter.java` — `@Component`. Private helper `render(Consumer<Document>)` with `Document + PdfWriter.getInstance(doc, baos)`. Public: `exportVentas`, `exportTopProductos`, `exportKardex`, `exportOrdenes` each `→byte[]`. Empty list → title + header table only → valid `%PDF`. [REQ-RPT-05]
- [x] P5.5 Run `cd backend && ./mvnw test` — 285 tests GREEN (42 new: 24 service + 10 Excel + 8 PDF). BUILD SUCCESS.

---

## Phase 6: Controller + GlobalExceptionHandler (RED → GREEN)

- [x] P6.1 **[RED]** Create `backend/src/test/java/pe/com/krypton/controller/AdminReportControllerTest.java` — `@WebMvcTest(AdminReportController.class)` with `addFilters=false`, excluding `JwtAuthenticationFilter`, mocking `ReportService`, `ExcelExporter`, `PdfExporter`. 16 tests: 200+Content-Type+Content-Disposition per endpoint; 400 for missing required params (desde/hasta R1, productId R3); 400 for invalid status (IllegalArgumentException from service); default granularidad "dia" verified; default limit 10 verified. NOTE: 403 CLIENTE tests moved to ReportIntegrationTest (P7) — addFilters=false disables FilterSecurityInterceptor, mirroring AdminOrderControllerTest W-01 trade-off. [REQ-RPT-05, REQ-RPT-07]
- [x] P6.2 Modify `backend/src/main/java/pe/com/krypton/exception/GlobalExceptionHandler.java` — added `MissingServletRequestParameterException` → 400 "Parámetro requerido ausente: {name}"; `MethodArgumentTypeMismatchException` → 400 "Valor inválido para el parámetro '{name}': {value}"; `IllegalArgumentException` → 400 with ex.getMessage(). All return `ApiError` record. [REQ-RPT-07]
- [x] P6.3 **[GREEN]** Create `backend/src/main/java/pe/com/krypton/controller/AdminReportController.java` — 8 endpoints path-per-format under `/api/admin/reports`. Private `file(byte[], String, MediaType)` helper sets Content-Disposition via `ContentDisposition.attachment()`. Filename conventions: ventas_{desde}_{hasta}.ext; productos_vendidos_{desde}_{hasta|todos}.ext; kardex_{productId}_{desde}_{hasta|todos}.ext; ordenes_{desde}_{hasta|todos}.ext. [REQ-RPT-01..06]
- [x] P6.4 Run `cd backend && ./mvnw test` — 301 tests, 0 failures, BUILD SUCCESS.

---

## Phase 7: E2E Integration Test (Optional — HIGH value, covers REQ-RPT-06)

- [x] P7.1 **[RED]** Create `backend/src/test/java/pe/com/krypton/ReportIntegrationTest.java` extending `AbstractIntegrationTest`. 6 tests: admin JWT → ventas/excel (200 XLSX PK magic, spreadsheetml, attachment disposition); admin JWT → ventas/pdf (200 %PDF magic); CLIENTE → ventas/excel (403); CLIENTE → ordenes/pdf (403); anon → ventas/excel (401); anon → ordenes/pdf (401). IT-RPT- prefix cleanup in @AfterEach. [REQ-RPT-05, REQ-RPT-06]
- [x] P7.2 **[GREEN]** ReportIntegrationTest passes with no new prod code — security from SecurityConfig; binary from AdminReportController P6.
- [x] P7.3 Run `cd backend && ./mvnw test` — 307 tests, 0 failures, BUILD SUCCESS.

---

## Phase 8: Final Validation Gate

- [x] P8.1 Run `cd backend && ./mvnw test` — 307 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS.
- [x] P8.2 V5 migration does NOT exist. Only V1–V4 in db/migration/. [REQ-RPT-08]
- [x] P8.3 `OrderService.java` interface UNCHANGED — 7 methods same as before batch 3. [REQ-RPT-08]
- [x] P8.4 Handing off to `sdd-verify` with spec `sdd/reportes/spec` (#325) and tasks `sdd/reportes/tasks` (#327).

---

## Parallelism Notes

- P2 (DTOs) is fully sequential but fast — no external deps.
- P3.1/P3.2 (OrderSpecification) can run independently of P3.3–P3.8 (repo queries) — both merge before P3.9 gate.
- P5.1 (ExcelExporterTest RED) and P5.2 (PdfExporterTest RED) can be written simultaneously.
- P6.1 (controller test RED) can be written simultaneously with P6.2 (GlobalExceptionHandler) since they touch different files.
- P7 is optional — P8 does not depend on it.

## Risk Register

| Risk | Mitigation |
|------|-----------|
| R2 type-widening: Hibernate may return `Long` for `SUM(quantity)` and `Double` for `SUM(quantity*unitPrice)` in JPQL | P3.3 asserts types explicitly; if widened, fix `TopProductoRow` in P2.3 before P3.9 gate |
| `date_trunc` with `:gran` param may be rejected by some PG JDBC drivers as non-literal | R1 native query passes `"day"`/`"month"` as `:gran`; if rejected switch to two `@Query` overloads |
| OpenPDF 2.0.x `PdfWriter.getInstance` API differs from iText 2.x | Verify package `com.lowagie.text` at P5.3/P5.4 compile time; test %PDF magic bytes at P5.2 |
| `@WebMvcTest` with security auto-config may still load `JwtAuthenticationFilter` | Use `excludeAutoConfiguration = {SecurityAutoConfiguration.class, ...}` or `addFilters=false` — mirror `AdminOrderControllerTest` exactly |
| Lima TZ boundary off-by-one (hasta end = next-day exclusive) | P3.3 seeds orders crossing Lima midnight; P4.1 asserts exact Instant boundary math |
