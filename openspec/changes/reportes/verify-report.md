# Verify Report: reportes

**Verdict: PASS WITH WARNINGS**
**Date**: 2026-06-09
**Test run**: 307 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS

---

## Counts

- CRITICAL: 0
- WARNING: 2
- SUGGESTION: 1

---

## Test Run Evidence

| Test Class | Tests | Result |
|---|---|---|
| AdminReportControllerTest | 16 | PASS |
| ReportIntegrationTest | 6 | PASS |
| ReportRepositoryIntegrationTest | 11 | PASS |
| ReportServiceImplTest | 24 | PASS |
| ExcelExporterTest | 10 | PASS |
| PdfExporterTest | 8 | PASS |
| OrderSpecificationTest | 8 | PASS |
| all pre-existing suites | 224 | PASS |
| **TOTAL** | **307** | **BUILD SUCCESS** |

---

## Requirements Traceability

| REQ | Status | Covering Tests |
|---|---|---|
| REQ-RPT-01 Ventas por periodo | PASS | ReportServiceImplTest (lima boundary, gran mapping, desde>hasta), ReportRepositoryIntegrationTest (native query, Lima day-bucket, CONFIRMADA only), AdminReportControllerTest (200 xlsx/pdf, Content-Disposition, default granularidad=dia, missing desde/hasta -> 400), ReportIntegrationTest (admin binary) |
| REQ-RPT-02 Top productos | PASS | ReportServiceImplTest (limit>100, limit=0, limit<0, partial date, pageable forwarded), AdminReportControllerTest (200, default limit=10), ExcelExporterTest, PdfExporterTest |
| REQ-RPT-03 Kardex por producto | PASS | ReportServiceImplTest (404 product not found, partial date range, movements mapped), AdminReportControllerTest (200, missing productId -> 400), ExcelExporterTest, PdfExporterTest |
| REQ-RPT-04 Listado ordenes | PASS | ReportServiceImplTest (invalid status, no filters, partial date), OrderSpecificationTest (null predicates), ReportRepositoryIntegrationTest (spec composition), AdminReportControllerTest (200, invalid status -> 400) |
| REQ-RPT-05 HTTP response contract | WARN W-01 | AdminReportControllerTest (Content-Type, Content-Disposition, no 204), ReportIntegrationTest (XLSX PK magic, PDF %PDF magic), ExcelExporterTest (XSSFWorkbook-parseable), PdfExporterTest (%PDF) -- WARNING: 400 body uses field "error" not "message" |
| REQ-RPT-06 Authorization | PASS | ReportIntegrationTest (CLIENTE -> 403 x2, anon -> 401 x2), AdminReportControllerTest (@WithMockUser ADMIN -> 200) |
| REQ-RPT-07 Param validation -> 400 | WARN W-01 | AdminReportControllerTest (missing required params -> 400 $.error), ReportServiceImplTest (todas las validaciones) -- same W-01 field name mismatch |
| REQ-RPT-08 Read-only | PASS | No V5 migration, OrderService unchanged, ddl-auto:validate, @Transactional(readOnly=true) |

---

## Findings

### WARNING W-01 -- 400 body field is "error", spec says "message"

REQ-RPT-07 (spec line 637) requires a JSON body with a human-readable "message" field on all 400 responses.
The ApiError record uses "error" as the field name. Tests check $.error and pass, but the external contract
diverges from the spec.

Where: backend/src/main/java/pe/com/krypton/exception/ApiError.java

Impact: API clients expecting $.message will get null. This is a pre-existing convention across the whole
backend (ApiError predates this change), not a regression introduced by reportes.

Recommendation: Align spec to "error" (lowest risk, updates 1 word in spec) OR rename ApiError.error -> 
ApiError.message and update all $.error test assertions. Pick one and apply consistently.

---

### WARNING W-02 -- Design doc filename example says "top-productos", spec and code say "productos_vendidos"

The design artifact (sdd/reportes/design) shows filename token "top-productos.*" in its controller example.
The spec REQ-RPT-05 defines "productos_vendidos_{suffix}.{ext}". The controller correctly implements
"productos_vendidos_" per spec. Zero runtime impact.

Where: openspec/changes/reportes/design.md (controller section, documentation drift only)

Recommendation: Update design.md filename example at archive time.

---

### SUGGESTION S-01 -- No dedicated test for desde==hasta same-day valid range

The spec notes "desde==hasta (same day) is valid." No test explicitly asserts the non-exception path
for this boundary in a named test. The Lima-boundary test incidentally uses desde=hasta=2024-03-01 but
for Instant-math verification, not boundary acceptance.

Where: backend/src/test/java/pe/com/krypton/service/ReportServiceImplTest.java

Recommendation: Add ventasPorPeriodo_mismo_dia_es_valido asserting no exception when desde==hasta.

---

## Task Completeness

openspec/changes/reportes/tasks.md: 40 of 40 tasks marked [x]. No incomplete tasks.

---

## Architecture Conformance

- Controller injects only ReportService + ExcelExporter + PdfExporter, never touches repos directly. PASS.
- ReportServiceImpl returns DTOs only (records). R4 maps Order -> OrderResponse via OrderMapper. PASS.
- ExcelExporter and PdfExporter are @Component consuming DTOs, producing byte[]. PASS.
- ReportService is interface + impl. PASS.
- @Transactional(readOnly=true) on ReportServiceImpl. PASS.
- OrderSpecification mirrors ProductSpecification null-predicate pattern. PASS.

---

## Read-Only Invariants

| Invariant | Result |
|---|---|
| No V5 Flyway migration | PASS -- only V1-V4 exist |
| OrderService interface unchanged | PASS -- 7 methods, identical signatures |
| ddl-auto: validate | PASS -- confirmed in application.yml |
| No writes in reporting layer | PASS -- readOnly=true, only SELECT operations |

---

## Carry-Forward Checks

| Item | Result |
|---|---|
| TopProductoRow.unidades is Long (boxed) | PASS -- Hibernate 6.x SUM widening confirmed, documented in class Javadoc |
| Lima TZ math covered by IT | PASS -- ReportRepositoryIntegrationTest seeds orders crossing Lima midnight; ReportServiceImplTest asserts exact Instants (start 2024-03-01T05:00:00Z, end 2024-03-02T05:00:00Z) |
| Empty-document behavior tested | PASS -- ExcelExporterTest: empty list -> XSSFWorkbook header-only row count=1; PdfExporterTest: empty list -> valid %PDF |

---

## Next Recommended

sdd-archive -- no CRITICAL issues. All 307 tests pass. W-01 and W-02 are non-blocking.
