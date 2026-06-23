# Exploration: reportes — Exportable Admin Reports (Excel + PDF)

> SDD explore phase. Artifact store: hybrid (engram `sdd/reportes/explore` #323 + this file).

## Current State

Spring Boot 3.3.5 / Java 17 / PostgreSQL 16. Migrations V1–V4 applied, `ddl-auto: validate`.
This feature is **100% read-only** — no new migration needed. Neither Apache POI nor OpenPDF
are in `backend/pom.xml` (confirmed by inspection). `docs/arquitectura-backend.md` already
anticipates `ReportService` / `ReportController` in the package tree.

## 1. Exact Schema — Relevant Columns

- **orders:** `id`, `user_id` (FK), `order_date` TIMESTAMPTZ, `status` VARCHAR(20) {PENDIENTE, CONFIRMADA, CANCELADA}, `total` NUMERIC(12,2)
- **order_items:** `id`, `order_id` (FK), `product_id` (FK), `quantity` INTEGER, `unit_price` NUMERIC(12,2) — price snapshot at checkout
- **products:** `id`, `sku` VARCHAR(60), `name` VARCHAR(150), `price` NUMERIC(12,2), `stock` INTEGER (cached), `active` BOOLEAN, `category_id` (FK)
- **stock_movement:** `id`, `product_id` (FK), `type` VARCHAR(20) {ENTRADA, SALIDA}, `quantity` INTEGER, `reason` VARCHAR(120), `reference` VARCHAR(120), `created_at` TIMESTAMPTZ, `created_by` BIGINT (nullable)
- **categories:** `id`, `name` VARCHAR(80)

JPA entities confirmed: `Order` (orderDate: Instant, status: OrderStatus enum, total: BigDecimal),
`OrderItem` (quantity: int, unitPrice: BigDecimal, product: LAZY), `Product`, `StockMovement`
(type: MovementType enum, createdAt: Instant, createdBy: LAZY nullable), `Category`.

## 2. Existing Code to Reuse

- `OrderService.getAllOrders(Pageable)` + `OrderServiceImpl` + `AdminOrderController` + `OrderMapper` + `OrderResponse` — foundation for Report 4.
- `ProductSpecification` pattern (JpaSpecificationExecutor + factory statics) — mirror as `OrderSpecification`.
- `AbstractIntegrationTest` (Testcontainers postgres:16 singleton) — reuse for repository @Query tests.
- `PageResponse<T>` generic wrapper.
- `SecurityConfig`: `/api/admin/**` → `hasRole("ADMIN")` — admin report endpoints protected automatically.

Repository gaps (must add, all additive):
- `OrderRepository`: no date-range filter, no aggregation @Query, not yet `JpaSpecificationExecutor<Order>`.
- `OrderItemRepository`: no product-grouped aggregation query.
- `StockMovementRepository`: needs `findByProductAndCreatedAtBetween`.

## 3. Aggregation Queries Per Report

- **R1 Ventas por período:** native SQL (`date_trunc('day'|'month', order_date)`); scalar totals (COUNT/SUM/AVG) + breakdown. Filter `status='CONFIRMADA'` + date range.
- **R2 Productos más vendidos:** JPQL GROUP BY `oi.product.id`, SUM quantity + SUM(quantity*unitPrice), JOIN filter `o.status='CONFIRMADA'` + date range, top-N via `Pageable`.
- **R3 Inventario/Kardex:** current stock from `ProductRepository`; movement history via `StockMovementRepository.findByProductAndCreatedAtBetween`.
- **R4 Listado de órdenes:** add `JpaSpecificationExecutor<Order>` + `OrderSpecification` (hasStatus, dateBetween, hasUser).

## 4. Library Decision

- **Apache POI (xlsx):** `org.apache.poi:poi-ooxml:5.5.1` — latest stable, Java 17 OK, no Spring conflicts. `XSSFWorkbook`.
- **OpenPDF (pdf):** `com.github.librepdf:openpdf:2.0.3` — last Java-17-compatible branch (2.1.x/3.x require Java 21). iText-2 fork, no iText-5 CVEs. `Document`/`PdfWriter`/`PdfPTable`.
- Both confirmed NOT in pom.xml.

## 5. Structural Approach — Recommended (Approach B)

```
pe.com.krypton
├── service/
│   ├── ReportService.java         (interface — 4 data methods returning report DTOs)
│   └── impl/ReportServiceImpl.java
├── report/
│   ├── ExcelExporter.java         (@Component — 4 methods, one per report)
│   └── PdfExporter.java           (@Component — 4 methods, one per report)
├── controller/
│   └── AdminReportController.java (/api/admin/reports — 8 endpoints: 4 reports × 2 formats)
└── dto/response/report/
    ├── VentasPorPeriodoReport.java
    ├── TopProductosReport.java
    ├── KardexReport.java
    └── OrdenesListadoReport.java
```

Service returns typed DTOs; exporters consume them (SRP, independently testable). One ExcelExporter
+ one PdfExporter, each with one method per report (avoids generic-exporter type-safety loss).

## 6. Binary Response Handling

`ResponseEntity<byte[]>` with `Content-Disposition: attachment; filename=...` and Content-Type
`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (xlsx) / `application/pdf`.
In-memory `ByteArrayOutputStream` is correct at academic scale; note `SXSSFWorkbook` as future
streaming option (tech debt).

## 7. Testing Under Strict TDD

| Layer | Test Type | Proves |
|-------|-----------|--------|
| Repository @Query | @DataJpaTest + Testcontainers Postgres | native SQL + JPQL aggregation, date_trunc, GROUP BY (H2 can't) |
| ReportServiceImpl | Mockito unit | DTO assembly, param forwarding |
| ExcelExporter | pure Java unit | byte[] → XSSFWorkbook → assert cells |
| PdfExporter | pure Java unit | bytes[0..3] == `%PDF` + non-empty |
| AdminReportController | @WebMvcTest | 200, Content-Type, Content-Disposition |
| Integration (optional) | AbstractIntegrationTest | end-to-end with admin JWT |

## Risks / Open Decisions

| # | Risk | Severity |
|---|------|----------|
| R-1 | Timezone for `date_trunc`: UTC vs `AT TIME ZONE 'America/Lima'` (orders 8–11pm Lima fall on next UTC day) | Med |
| R-2 | `OrderRepository` needs `JpaSpecificationExecutor<Order>` (additive) | Low |
| R-3 | Native SQL required for date_trunc (Postgres-only project — acceptable) | Low |
| R-4 | OpenPDF 2.0.x is the Java-17 ceiling | Low |
| R-5 | Kardex scope: all products vs single `productId` filter | Med |
| R-6 | Report 4: extend `OrderService.getAllOrders` vs new isolated `ReportService.listadoOrdenes` (recommend latter) | Med |
| R-7 | Empty results: 200 with empty sheet/page vs 204 (recommend 200) | Low |

## Ready for Proposal

Yes — no blockers. Open decisions (R-1, R-5, R-6, R-7) resolvable in proposal with defaults.
