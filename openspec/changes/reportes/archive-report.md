# Archive Report: reportes — Exportable Admin Reports (Excel + PDF)

**Change Status**: ARCHIVED AND CLOSED
**Archive Date**: 2026-06-09
**Verdict**: PASS (no CRITICAL, warnings W-01/W-02 + suggestion S-01 all RESOLVED in cleanup) — 308/308 tests GREEN
**Change Name**: `reportes`
**Capability**: `reporting` (NEW)

---

## Executive Summary

The `reportes` change introduces the `reporting` capability: 8 ADMIN-only REST endpoints under
`/api/admin/reports` for exporting reports in Excel (.xlsx) or PDF. The feature is 100% read-only,
adds NO new Flyway migration, leaves the `OrderService` contract untouched, and passes all
**308 tests** (0 failures, 0 CRITICAL). The two warnings and one suggestion raised by verify were
all resolved in a cleanup pass before archive. Delta spec synced to source of truth at
`openspec/specs/reporting/spec.md`.

---

## SDD Artifact Traceability

| Artifact | Engram ID | Location | Status |
|----------|-----------|----------|--------|
| Exploration | #323 | `openspec/changes/reportes/explore.md` | Completed |
| Proposal | #324 | `openspec/changes/reportes/proposal.md` | Completed |
| Spec (Delta) | #325 | `openspec/changes/reportes/specs/reporting/spec.md` | Synced to main |
| Design | #326 | `openspec/changes/reportes/design.md` | Completed |
| Tasks | #327 | `openspec/changes/reportes/tasks.md` | 40/40 [x] |
| Apply Progress | #328 | (engram only) | Completed (3 batches + cleanup) |
| Verify Report | #329 | `openspec/changes/reportes/verify-report.md` | PASS WITH WARNINGS (since resolved) |
| Archive Report | #330 | `openspec/changes/reportes/archive-report.md` (this) | Closed |

Convention note: this project keeps archived changes in `openspec/changes/<name>/` (matching
`carrito` and `ordenes`) with a `state.yaml` marking `status: archived` — it does NOT move folders
into an `archive/` subdirectory.

---

## 4 Reports, 8 Endpoints (`/api/admin/reports`, ADMIN-only)

- **R1 Ventas por período** — `GET /ventas/excel`, `/ventas/pdf`. Params: `desde` (required), `hasta` (required), `granularidad` (default `dia`). CONFIRMADA-only, `date_trunc` grouping in `America/Lima`.
- **R2 Productos más vendidos** — `GET /productos-vendidos/excel`, `/pdf`. Params: `limit` (default 10, max 100), `desde`/`hasta` (optional, both-or-neither). Top-N by units then revenue.
- **R3 Kardex por producto** — `GET /kardex/excel`, `/pdf`. Params: `productId` (required, 404 if missing), optional date range. Current stock + movement history.
- **R4 Listado de órdenes** — `GET /ordenes/excel`, `/pdf`. Params optional: `status`, `desde`, `hasta`, `userId`. Isolated in `ReportService.listadoOrdenes` (OrderService untouched).

## Layers Implemented

| Layer | Classes |
|-------|---------|
| Repository (additive) | `OrderRepository` (+native R1 query, +`JpaSpecificationExecutor<Order>`), `OrderItemRepository` (+JPQL top-products), `StockMovementRepository` (+derived), `spec/OrderSpecification`, `VentasPeriodoProjection`, `VentasTotalesProjection` |
| DTOs (7 records) | `VentasPorPeriodoReport`, `VentasPeriodoRow`, `TopProductosReport`, `TopProductoRow`, `KardexReport`, `KardexMovimientoRow`, `OrdenesListadoReport` |
| Service | `ReportService` (interface) + `ReportServiceImpl` (`@Service`, `@Transactional(readOnly=true)`) |
| Exporters | `ExcelExporter` (`@Component`, POI XSSFWorkbook), `PdfExporter` (`@Component`, OpenPDF) |
| Controller | `AdminReportController` (8 endpoints, path-per-format) |
| Exception handling | `GlobalExceptionHandler` +3 handlers (MissingServletRequestParameter, MethodArgumentTypeMismatch, IllegalArgument → 400) |

## Dependencies Added

- `org.apache.poi:poi-ooxml:5.5.1` (Excel — `dependency:tree` clean vs Boot 3.3.5 BOM)
- `com.github.librepdf:openpdf:2.0.3` (PDF — 2.0.x is the last Java-17-compatible branch)

---

## Test Coverage — 308 total, BUILD SUCCESS

| Layer | Test Class | Count |
|-------|-----------|-------|
| Repository | ReportRepositoryIntegrationTest (Testcontainers PG16) | 11 |
| Spec | OrderSpecificationTest (unit) | 8 |
| Service | ReportServiceImplTest (Mockito) | 25 |
| Exporter (Excel) | ExcelExporterTest (XSSFWorkbook re-parse) | 10 |
| Exporter (PDF) | PdfExporterTest (%PDF magic + size) | 8 |
| Controller | AdminReportControllerTest (@WebMvcTest) | 16 |
| Integration | ReportIntegrationTest (full JWT chain) | 6 |
| Pre-existing | all prior suites | 224 |
| **TOTAL** | | **308** |

Net new for this change: +65 tests (243 baseline → 308).

---

## Verify Findings — ALL RESOLVED

| ID | Finding | Resolution |
|----|---------|-----------|
| W-01 | 400 body field: spec said `message`, backend convention is `error` | Spec corrected to `error` (REQ-RPT-07) — matches the backend-wide `ApiError` convention. Code untouched (correct as-is). |
| W-02 | design.md filename example `top-productos` vs implemented `productos_vendidos` | design.md corrected to `productos_vendidos_`. |
| S-01 | No dedicated test for `desde == hasta` (same-day valid) | Added `ventasPorPeriodo_sameDayRange_desdeEqualsHasta_isValid` to ReportServiceImplTest (→ 25 tests). |

Key technical resolution during apply: Hibernate 6.x boxes `SUM(int)` to `Long` in JPQL
constructor-expressions, so `TopProductoRow.unidades` is `Long` (not `long`) — confirmed against
real Postgres by `ReportRepositoryIntegrationTest`, not assumed. The `date_trunc(:gran, ...)`
bound-param works on the Postgres 16 JDBC driver; no fallback needed.

---

## Read-Only Invariants Verified

| Invariant | Result |
|-----------|--------|
| No V5 migration (only V1–V4) | PASS |
| OrderService frozen (7 methods, unchanged signatures) | PASS |
| ddl-auto: validate | PASS |
| No write operations (`@Transactional(readOnly=true)`, SELECT-only) | PASS |

## Architecture Conformance

Layered (controller → service → repository), DTOs only (no `@Entity` exposed), exporters are
`@Component` consuming DTOs, `OrderSpecification` mirrors `ProductSpecification`, all repo changes
additive. Authorization handled by existing `SecurityConfig` (`/api/admin/**` → `hasRole("ADMIN")`).

---

## Production Readiness

- [x] 308 tests GREEN, 0 failures, 0 CRITICAL
- [x] W-01, W-02, S-01 resolved
- [x] Delta spec synced to `openspec/specs/reporting/spec.md`
- [x] state.yaml marks `status: archived` (convention matches carrito/ordenes)
- [x] Archive report persisted (filesystem + engram #330)
- [x] No new migration, no breaking changes — safe to merge/deploy

**Rollback**: drop the 8 endpoints, the reporting DTOs/exporters/service, the additive repo
methods, and the 2 pom deps. No DB cleanup needed.

---

*Archive report created 2026-06-09. SDD cycle complete: explore → propose → spec → design → tasks → apply → verify → archive.*
