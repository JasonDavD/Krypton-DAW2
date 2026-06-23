# Proposal: reportes — Exportable Admin Reports (Excel + PDF)

> SDD propose phase. Artifact store: hybrid (engram `sdd/reportes/proposal` + this file).
> Grounded in exploration `sdd/reportes/explore` (#323 / `explore.md`).

## Intent

**Problem.** The Krypton backend has confirmed orders, order items, products, and stock
movements persisted, but ADMIN users have no way to extract that operational data as
shareable, offline documents. There is no billing summary, no best-sellers view, no
per-product stock audit trail, and no filterable order export. Management cannot answer
"how much did we bill last month?" or "which products move?" without querying the database
directly.

**Why now.** The order lifecycle (checkout → CONFIRMADA, simulated payment) and the stock
movement ledger are now complete and stable. That data is the substrate for reporting. This
is the natural next capability and the last major piece for the academic project's admin
backoffice. The feature is **100% read-only** — it adds value without touching any write
path or migration.

**Success looks like.** An authenticated ADMIN can hit eight endpoints under
`/api/admin/reports` and receive a correctly-aggregated `.xlsx` or `.pdf` document for each
of four reports. Aggregations are validated against real PostgreSQL (not H2). Dates are
grouped in Lima local time. Empty filters return a valid empty document (HTTP 200), never an
error. No existing service contract is disturbed.

## Scope

### In scope

- **Four reports, each in both Excel (.xlsx) and PDF → 8 REST endpoints** under
  `/api/admin/reports`, ADMIN-only (security already enforced by `/api/admin/**`).
  - **R1 Ventas por período** — total billed in a date range, `status = CONFIRMADA` only;
    breakdown by day or month, order count, average ticket.
  - **R2 Productos más vendidos** — top-N by units sold and revenue in a date range, from
    `order_items` joined to CONFIRMADA orders.
  - **R3 Inventario / Kardex (per product)** — receives a `productId`; returns current
    stock plus movement history (entradas/salidas with date, reason, reference). Single
    product, NOT all products.
  - **R4 Listado de órdenes** — admin order listing export with filters (status, date
    range, userId).
- New `reporting` capability: `ReportService` (interface + impl) returning typed report
  DTOs; `ExcelExporter` and `PdfExporter` `@Component`s (one method per report);
  `AdminReportController`.
- Additive repository changes (new aggregation `@Query` methods, `JpaSpecificationExecutor<Order>`,
  `OrderSpecification`, `StockMovementRepository.findByProductAndCreatedAtBetween`).
- New dependencies in `backend/pom.xml`: `org.apache.poi:poi-ooxml:5.5.1` and
  `com.github.librepdf:openpdf:2.0.3`.
- Date grouping in `America/Lima` via native SQL `AT TIME ZONE`.

### Out of scope (explicit)

- **All-products inventory snapshot.** R3 is strictly per-product (requires `productId`).
  A global stock report across the whole catalog is NOT part of this change.
- **Real payment gateway.** Payment remains simulated; reports read the existing persisted
  `total`/`status` — no integration with any external payment processor.
- **Scheduled or emailed reports.** No cron, no `@Scheduled`, no SMTP delivery. Reports are
  generated synchronously on request only.
- **Streaming generation.** In-memory `ByteArrayOutputStream` only. `SXSSFWorkbook` streaming
  is deferred (see Risks).
- **No new Flyway migration.** The feature is read-only over existing tables; `V5` is NOT
  created. `ddl-auto: validate` stays untouched.
- **Front-end / dashboard rendering, charts, CSV/other formats.** Only `.xlsx` and `.pdf`.
- **Modifying `OrderService`.** R4 gets a new `ReportService.listadoOrdenes(filtros)` method
  — the existing, well-tested `OrderService` contract is not changed.

## Capabilities affected

| Capability | Effect |
|------------|--------|
| **`reporting`** (NEW) | The whole feature. New service + exporters + controller + report DTOs. |
| `orders` | Read-only reuse of `Order`/`OrderItem`/`OrderStatus`. **Additive** repo changes only (`JpaSpecificationExecutor`, aggregation queries). `OrderService` contract untouched. |
| `catalog` (products) | Read-only reuse of `Product.stock`, `sku`, `name`, `Category.name`. No changes to product write paths. |
| `inventory` (stock) | Read-only reuse of `StockMovement`. Additive `findByProductAndCreatedAtBetween` query. |
| `security` | No change — `/api/admin/**` already requires `hasRole("ADMIN")`. Reports inherit it for free. |
| `build` | `backend/pom.xml` gains two dependencies. No migration, no config change. |

## Approach (Approach B — locked)

Layered design consistent with the existing catalog/cart/orders changes. The service owns
**data**; the exporters own **bytes** (SRP, independently testable). Controller orchestrates
both beans and shapes the HTTP binary response.

```
pe.com.krypton
├── service/
│   ├── ReportService.java          (interface — 4 methods, return typed report DTOs)
│   └── impl/ReportServiceImpl.java (queries repos, assembles DTOs, NO byte rendering)
├── report/
│   ├── ExcelExporter.java          (@Component — 4 methods, one per report → byte[])
│   └── PdfExporter.java            (@Component — 4 methods, one per report → byte[])
├── controller/
│   └── AdminReportController.java  (/api/admin/reports — 8 endpoints)
├── repository/                     (ADDITIVE only)
│   ├── OrderRepository.java        + JpaSpecificationExecutor<Order> + native aggregation @Query (R1)
│   ├── OrderItemRepository.java    + grouped aggregation @Query (R2 top-products)
│   ├── StockMovementRepository.java + findByProductAndCreatedAtBetween (R3)
│   └── spec/OrderSpecification.java (NEW — mirrors ProductSpecification: hasStatus, dateBetween, hasUser)
└── dto/response/report/            (NEW package — typed records)
    ├── VentasPorPeriodoReport.java
    ├── TopProductosReport.java
    ├── KardexReport.java
    └── OrdenesListadoReport.java
```

**Flow per request:** controller parses params → `reportService.<report>(...)` returns a
typed DTO → `excelExporter.<report>(dto)` or `pdfExporter.<report>(dto)` returns `byte[]` →
controller returns `ResponseEntity<byte[]>` with `Content-Type` +
`Content-Disposition: attachment; filename="..."`.

**Query strategy:**
- R1: native SQL with `date_trunc('day'|'month', order_date AT TIME ZONE 'America/Lima')`,
  `status = 'CONFIRMADA'`, date range. Plus scalar totals (COUNT/SUM/AVG).
- R2: JPQL `GROUP BY oi.product.id`, `SUM(quantity)` + `SUM(quantity*unitPrice)`, JOIN filter
  on CONFIRMADA + date range, top-N via `Pageable`.
- R3: current stock from `ProductRepository`; history via
  `StockMovementRepository.findByProductAndCreatedAtBetween`.
- R4: `OrderRepository.findAll(spec, pageable)` with `OrderSpecification` composing
  status/date/user filters.

### Endpoint list (8 endpoints)

**URL convention — RECOMMENDED: distinct path per format** (`/ventas/excel`, `/ventas/pdf`)
rather than a `?formato=xlsx|pdf` query param.

Rationale: the response media type and `Content-Disposition` filename extension differ per
format, so the format is part of the **resource identity**, not a filter over the same
resource. Distinct paths make `@GetMapping(produces = ...)` explicit per endpoint, keep
each controller method single-purpose and trivially testable in a `@WebMvcTest` slice, and
avoid runtime branching on a string param (which would force one method to juggle two media
types). The exploration's example used both styles; we lock distinct paths for clarity and
testability.

| # | Report | Excel | PDF |
|---|--------|-------|-----|
| R1 | Ventas por período | `GET /api/admin/reports/ventas/excel?desde=&hasta=&granularidad=DAY\|MONTH` | `GET /api/admin/reports/ventas/pdf?desde=&hasta=&granularidad=DAY\|MONTH` |
| R2 | Productos más vendidos | `GET /api/admin/reports/productos-vendidos/excel?desde=&hasta=&limit=10` | `GET /api/admin/reports/productos-vendidos/pdf?desde=&hasta=&limit=10` |
| R3 | Inventario / Kardex | `GET /api/admin/reports/kardex/excel?productId=&desde=&hasta=` | `GET /api/admin/reports/kardex/pdf?productId=&desde=&hasta=` |
| R4 | Listado de órdenes | `GET /api/admin/reports/ordenes/excel?status=&desde=&hasta=&userId=` | `GET /api/admin/reports/ordenes/pdf?status=&desde=&hasta=&userId=` |

Param defaults to be finalized in spec: R1 `granularidad` default `DAY`; R2 `limit` default
`10`, max `100`; R3 `productId` required, `desde`/`hasta` optional; R4 all filters optional.
All eight: empty result set → **HTTP 200 with a valid empty sheet/PDF** (locked, not 204).

## Risks & mitigations

| # | Risk | Severity | Resolution / Mitigation |
|---|------|----------|-------------------------|
| R-1 | Timezone for `date_trunc` — `order_date` is TIMESTAMPTZ; UTC grouping puts late-evening Lima orders on the next day. | Med | **RESOLVED → group with `AT TIME ZONE 'America/Lima'`** (locked decision 2). Native SQL required to express it. Document in spec as the canonical grouping rule for R1. |
| R-2 | `date_trunc` not expressible in JPQL → must use native SQL. | Low | Accepted. Project is PostgreSQL-only (Testcontainers postgres:16), so native SQL is safe. Use `@Query(nativeQuery=true)` for R1 aggregation only; R2 stays JPQL. |
| R-3 | OpenPDF Java-17 ceiling — 2.1.x/3.x need Java 21; project is Java 17. | Low | **Pin `openpdf:2.0.3`** (last 2.0.x stable). Note the upgrade path requires a Java 21 bump first. iText-2 fork → no iText-5 CVEs. |
| R-4 | In-memory generation (`ByteArrayOutputStream`) won't scale to 100k+ rows. | Low | **Accepted at academic scale** (tens–hundreds of rows). Documented as tech debt; `SXSSFWorkbook` streaming is the future path. Out of scope now. |
| R-5 | `OrderRepository` not yet `JpaSpecificationExecutor` — needed for R4 dynamic filters. | Low | Additive, non-breaking. Add the interface + new `OrderSpecification` mirroring `ProductSpecification`. |
| R-6 | R4 could be wedged into `OrderService.getAllOrders`. | Med | **RESOLVED → new isolated `ReportService.listadoOrdenes(filtros)`** (locked decision 3). Keeps the stable order-management contract untouched. |
| R-7 | Empty-result document semantics. | — | **RESOLVED → HTTP 200 with empty document** (locked decision 4). |
| R-8 | New dependencies could conflict with the Spring Boot 3.3.5 BOM (POI pulls commons-compress, log4j-api, XMLBeans). | Low | Exploration confirmed no conflicts. Run `mvn dependency:tree` after adding; pin only the two GAVs, let transitives resolve. |
| R-9 | PDF content is hard to assert in tests. | Low | Exporter tests assert `%PDF` magic bytes + non-empty + row-count heuristic for PDF; full cell-value assertions reserved for Excel (parse `byte[]` back into `XSSFWorkbook`). |

## Out-of-scope / future

- Global all-products inventory snapshot report (R3 is per-product only now).
- Streaming export (`SXSSFWorkbook`) for large datasets.
- Scheduled / emailed report delivery.
- Additional export formats (CSV, JSON download).
- Charts/graphics inside the PDF, branded templates, multi-sheet workbooks beyond what each
  report needs.
- Front-end dashboard consuming these endpoints.
- OpenPDF 2.1.x/3.x upgrade (blocked on a Java 21 migration).

## Ready for next phase

Yes. All open decisions from exploration (R-1 timezone, R-5 kardex scope, R-6 R4 isolation,
R-7 empty results) are resolved by the locked decisions. No blockers. Feeds **sdd-spec** and
**sdd-design** (can run in parallel).
