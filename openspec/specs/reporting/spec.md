# Spec: reporting — Exportable Admin Reports (Excel + PDF)

> Capability: `reporting` (NEW). Synced from change `reportes` at 2026-06-09.
> Grounded in: proposal `sdd/reportes/proposal` (#324) + design `sdd/reportes/design` (#326).

---

## Scope

This spec describes what MUST be true once the `reportes` change is applied. It is a **full
capability spec** (no prior `reporting` spec exists). It does NOT prescribe how to implement —
that is the design phase's job.

The `reporting` capability adds 8 ADMIN-only endpoints under `/api/admin/reports` that return
binary documents (Excel or PDF) for four report types. The feature is **100% read-only**; it
touches no write path and requires no new Flyway migration.

---

## Requirements

### REQ-RPT-01 — R1: Ventas por período (Sales by period)

The system MUST expose two endpoints that return an aggregated sales report for a caller-
supplied date range, covering only orders with `status = CONFIRMADA`.

#### Endpoints
```
GET /api/admin/reports/ventas/excel
GET /api/admin/reports/ventas/pdf
```

#### Parameters

| Parameter | Type | Required | Default | Constraints |
|-----------|------|----------|---------|-------------|
| `desde` | `yyyy-MM-dd` | YES | — | Must be a valid date; `desde` ≤ `hasta` |
| `hasta` | `yyyy-MM-dd` | YES | — | Must be a valid date; `hasta` ≥ `desde` |
| `granularidad` | `dia` \| `mes` | NO | `dia` | Only these two values accepted |

**Date boundary mapping.** A `desde` / `hasta` value expressed as `yyyy-MM-dd` maps to the
inclusive day boundary in the `America/Lima` timezone:
- `desde=2026-01-01` → `2026-01-01T00:00:00-05:00` (= `2026-01-01T05:00:00Z`)
- `hasta=2026-01-31` → `2026-01-31T23:59:59.999999999-05:00` (= `2026-02-01T04:59:59...Z`)

Date grouping in the breakdown MUST use `AT TIME ZONE 'America/Lima'` on the `order_date`
column so that a Lima order placed at 11 PM does not appear on the next calendar day.

#### Document content (what the report MUST contain)

**Header / summary block:**
- Title: "Reporte de Ventas por Período"
- Date range (desde–hasta) as formatted strings (`dd/MM/yyyy`)
- Granularity label ("Por día" or "Por mes")
- Total order count (`COUNT` of CONFIRMADA orders in range)
- Total revenue (`SUM(total)` of CONFIRMADA orders in range, 2 decimal places)
- Average ticket (`AVG(total)` of CONFIRMADA orders in range, 2 decimal places)

**Breakdown table** (one row per period bucket):
- Columns: `Período`, `Nro. Órdenes`, `Ingresos (S/)`, `Ticket Promedio (S/)`
- Rows ordered chronologically (ascending by period)
- Period formatted as `dd/MM/yyyy` for `dia`, `MM/yyyy` for `mes`

**Only CONFIRMADA orders are counted.** PENDIENTE and CANCELADA orders MUST NOT appear in
any count or sum.

#### Validation errors → HTTP 400

- `desde` absent or not `yyyy-MM-dd` format
- `hasta` absent or not `yyyy-MM-dd` format
- `desde` after `hasta` (strict: `desde > hasta` is invalid)
- `granularidad` present but not `dia` or `mes` (case-insensitive matching allowed; reject
  anything else)

#### Scenarios

**SCENARIO RPT-01-01 — Happy path, daily granularity, Excel**
```
GIVEN  an authenticated ADMIN
  AND  orders exist with status=CONFIRMADA in the range 2026-01-01..2026-01-31
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-01-01&hasta=2026-01-31&granularidad=dia
THEN   HTTP 200
  AND  Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  AND  Content-Disposition: attachment; filename="ventas_2026-01-01_2026-01-31.xlsx"
  AND  response body is non-empty bytes starting with PK (OOXML magic)
  AND  workbook contains a sheet with header row ["Período","Nro. Órdenes","Ingresos (S/)","Ticket Promedio (S/)"]
  AND  each row period value matches Lima local date of the corresponding orders
  AND  SUM of "Ingresos (S/)" column equals total revenue of CONFIRMADA orders in range
```

**SCENARIO RPT-01-02 — Happy path, monthly granularity, PDF**
```
GIVEN  an authenticated ADMIN
  AND  orders exist with status=CONFIRMADA spanning multiple months in 2026
WHEN   GET /api/admin/reports/ventas/pdf?desde=2026-01-01&hasta=2026-03-31&granularidad=mes
THEN   HTTP 200
  AND  Content-Type: application/pdf
  AND  Content-Disposition: attachment; filename="ventas_2026-01-01_2026-03-31.pdf"
  AND  response body starts with "%PDF"
  AND  response body is non-empty (not zero bytes)
```

**SCENARIO RPT-01-03 — Granularidad defaults to "dia" when omitted**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-01-01&hasta=2026-01-31
         (no granularidad param)
THEN   HTTP 200
  AND  breakdown rows grouped by individual days (not months)
```

**SCENARIO RPT-01-04 — Empty result set → HTTP 200 with empty document**
```
GIVEN  an authenticated ADMIN
  AND  NO CONFIRMADA orders exist in the specified date range
WHEN   GET /api/admin/reports/ventas/excel?desde=2020-01-01&hasta=2020-01-02
THEN   HTTP 200
  AND  Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  AND  workbook is valid (parseable); breakdown section has 0 data rows
  AND  header/summary section shows total count = 0, revenue = 0.00, avg ticket = 0.00
```

**SCENARIO RPT-01-05 — Validation: desde absent → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ventas/excel?hasta=2026-01-31
THEN   HTTP 400
  AND  response body contains a message indicating "desde" is required
```

**SCENARIO RPT-01-06 — Validation: desde after hasta → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-02-01&hasta=2026-01-01
THEN   HTTP 400
  AND  response body contains a message indicating the date range is invalid
```

**SCENARIO RPT-01-07 — Validation: invalid granularidad value → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-01-01&hasta=2026-01-31&granularidad=week
THEN   HTTP 400
  AND  response body contains a message indicating valid values are "dia" or "mes"
```

**SCENARIO RPT-01-08 — Auth: CLIENTE role → 403**
```
GIVEN  an authenticated user with role CLIENTE
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-01-01&hasta=2026-01-31
THEN   HTTP 403
```

**SCENARIO RPT-01-09 — Auth: unauthenticated → 401**
```
GIVEN  no authentication token
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-01-01&hasta=2026-01-31
THEN   HTTP 401
```

---

### REQ-RPT-02 — R2: Productos más vendidos (Top-selling products)

The system MUST expose two endpoints returning a ranked list of the top-N products by units
sold and revenue, from `order_items` of CONFIRMADA orders in a date range.

#### Endpoints
```
GET /api/admin/reports/productos-vendidos/excel
GET /api/admin/reports/productos-vendidos/pdf
```

#### Parameters

| Parameter | Type | Required | Default | Constraints |
|-----------|------|----------|---------|-------------|
| `desde` | `yyyy-MM-dd` | NO | — | If provided must be valid date; `desde` ≤ `hasta` when both given |
| `hasta` | `yyyy-MM-dd` | NO | — | If provided must be valid date; `hasta` ≥ `desde` when both given |
| `limit` | integer | NO | `10` | Min 1, max 100; values outside range → 400 |

**Date range is optional.** When omitted, the report covers ALL time (no date filter). When
only one of `desde`/`hasta` is provided, both MUST be provided (partial range → 400).

**Ranking.** Products are ranked by total units sold (`SUM(quantity)`) descending. When units
are equal, break ties by total revenue descending. The result is capped at `limit` rows.

#### Document content

**Header:**
- Title: "Productos Más Vendidos"
- Date range (if provided), or "Todos los períodos"
- Top-N label: "Top N productos"

**Table columns:** `Rank`, `SKU`, `Nombre`, `Categoría`, `Unidades Vendidas`, `Ingresos (S/)`

**Only CONFIRMADA orders contribute.** Items from PENDIENTE or CANCELADA orders MUST NOT
appear.

#### Validation errors → HTTP 400

- `limit` present but not a positive integer
- `limit` > 100
- `desde` provided without `hasta`, or vice versa
- `desde` / `hasta` in wrong format
- `desde` after `hasta`

#### Scenarios

**SCENARIO RPT-02-01 — Happy path, with date range, Excel**
```
GIVEN  an authenticated ADMIN
  AND  several CONFIRMADA orders with items exist in Jan 2026
WHEN   GET /api/admin/reports/productos-vendidos/excel?desde=2026-01-01&hasta=2026-01-31&limit=5
THEN   HTTP 200
  AND  Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  AND  Content-Disposition: attachment; filename="productos_vendidos_2026-01-01_2026-01-31.xlsx"
  AND  workbook has at most 5 data rows
  AND  first row has the highest unit-sold count
  AND  "Ingresos (S/)" = SUM(quantity * unitPrice) for each product
```

**SCENARIO RPT-02-02 — Default limit (10), no date range, PDF**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/productos-vendidos/pdf
         (no params)
THEN   HTTP 200
  AND  Content-Type: application/pdf
  AND  report covers all time; at most 10 products in the document
  AND  Content-Disposition: attachment; filename="productos_vendidos_todos.pdf"
```

**SCENARIO RPT-02-03 — Empty result (no CONFIRMADA orders in range) → 200 with empty document**
```
GIVEN  an authenticated ADMIN
  AND  no CONFIRMADA orders exist in the specified range
WHEN   GET /api/admin/reports/productos-vendidos/excel?desde=2020-01-01&hasta=2020-01-01
THEN   HTTP 200
  AND  workbook is valid; 0 data rows in the table
```

**SCENARIO RPT-02-04 — Validation: limit > 100 → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/productos-vendidos/excel?limit=101
THEN   HTTP 400
  AND  message indicates limit must be between 1 and 100
```

**SCENARIO RPT-02-05 — Validation: limit = 0 → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/productos-vendidos/excel?limit=0
THEN   HTTP 400
```

**SCENARIO RPT-02-06 — Validation: desde without hasta → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/productos-vendidos/excel?desde=2026-01-01
THEN   HTTP 400
  AND  message indicates both desde and hasta must be provided together
```

**SCENARIO RPT-02-07 — Auth: CLIENTE → 403**
```
GIVEN  an authenticated CLIENTE
WHEN   GET /api/admin/reports/productos-vendidos/excel
THEN   HTTP 403
```

---

### REQ-RPT-03 — R3: Inventario / Kardex por producto

The system MUST expose two endpoints returning the current stock and movement history for a
single product identified by `productId`.

#### Endpoints
```
GET /api/admin/reports/kardex/excel?productId={id}[&desde=yyyy-MM-dd&hasta=yyyy-MM-dd]
GET /api/admin/reports/kardex/pdf?productId={id}[&desde=yyyy-MM-dd&hasta=yyyy-MM-dd]
```

#### Parameters

| Parameter | Type | Required | Default | Constraints |
|-----------|------|----------|---------|-------------|
| `productId` | Long | YES | — | Must be a positive integer; product must exist |
| `desde` | `yyyy-MM-dd` | NO | — | If provided, must be valid; `desde` ≤ `hasta` when both given |
| `hasta` | `yyyy-MM-dd` | NO | — | If provided, must be valid; `hasta` ≥ `desde` when both given |

**Date range on movements is optional.** When omitted, all movements for the product are
returned. Partial range (only one of `desde`/`hasta`) → 400.

**productId is always required.** The spec does NOT cover a global all-products kardex.

#### Document content

**Header block:**
- Title: "Kardex de Inventario"
- Product ID, SKU, Name, Category name
- Current stock (value from `products.stock`)
- Date range label (if filtered) or "Historial completo"

**Movement history table:**
- Columns: `Fecha`, `Tipo` (ENTRADA / SALIDA), `Cantidad`, `Razón`, `Referencia`
- Rows ordered by `created_at` ascending (oldest first)
- If date range is supplied, only movements within `[desde 00:00 Lima, hasta 23:59:59 Lima]` are shown

**Empty movement history (no movements in range) → valid document with 0 rows.** Current
stock header is always shown regardless of movement filter.

#### Product not found → HTTP 404

If no product with the given `productId` exists, the system MUST return HTTP 404 with a
message. No document is generated.

#### Validation errors → HTTP 400

- `productId` absent
- `productId` not a positive integer (e.g. negative, zero, non-numeric)
- `desde` present without `hasta`, or vice versa
- `desde` / `hasta` in wrong format
- `desde` after `hasta`

#### Scenarios

**SCENARIO RPT-03-01 — Happy path, with date range, Excel**
```
GIVEN  an authenticated ADMIN
  AND  product with id=7 (SKU="PROD-007") exists with stock=42
  AND  3 ENTRADA movements and 1 SALIDA movement exist for product 7 in Jan 2026
WHEN   GET /api/admin/reports/kardex/excel?productId=7&desde=2026-01-01&hasta=2026-01-31
THEN   HTTP 200
  AND  Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  AND  Content-Disposition: attachment; filename="kardex_7_2026-01-01_2026-01-31.xlsx"
  AND  header shows SKU="PROD-007", stock=42
  AND  movement table has exactly 4 rows ordered chronologically
```

**SCENARIO RPT-03-02 — No date range → all movements, PDF**
```
GIVEN  an authenticated ADMIN
  AND  product with id=3 exists with movements spread over multiple months
WHEN   GET /api/admin/reports/kardex/pdf?productId=3
THEN   HTTP 200
  AND  Content-Type: application/pdf
  AND  Content-Disposition: attachment; filename="kardex_3_todos.pdf"
  AND  response starts with "%PDF"
  AND  document is non-empty
```

**SCENARIO RPT-03-03 — Empty movement history → 200 with valid document**
```
GIVEN  an authenticated ADMIN
  AND  product with id=5 exists but has NO movements in the requested range
WHEN   GET /api/admin/reports/kardex/excel?productId=5&desde=2020-01-01&hasta=2020-01-31
THEN   HTTP 200
  AND  workbook is valid; movement table has 0 data rows
  AND  header block still shows product name and current stock
```

**SCENARIO RPT-03-04 — Product not found → 404**
```
GIVEN  an authenticated ADMIN
  AND  no product with id=9999 exists
WHEN   GET /api/admin/reports/kardex/excel?productId=9999
THEN   HTTP 404
  AND  response body contains a message indicating the product was not found
```

**SCENARIO RPT-03-05 — Validation: productId absent → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/kardex/excel
         (no productId)
THEN   HTTP 400
  AND  message indicates productId is required
```

**SCENARIO RPT-03-06 — Validation: productId = 0 → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/kardex/excel?productId=0
THEN   HTTP 400
```

**SCENARIO RPT-03-07 — Validation: partial date range → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/kardex/excel?productId=7&desde=2026-01-01
         (hasta missing)
THEN   HTTP 400
  AND  message indicates both desde and hasta must be provided together
```

**SCENARIO RPT-03-08 — Auth: CLIENTE → 403**
```
GIVEN  an authenticated CLIENTE
WHEN   GET /api/admin/reports/kardex/excel?productId=7
THEN   HTTP 403
```

---

### REQ-RPT-04 — R4: Listado de órdenes

The system MUST expose two endpoints that return a filterable export of all orders. All
filters are optional. The query MUST be isolated in `ReportService.listadoOrdenes(filtros)`;
`OrderService` is NOT modified.

#### Endpoints
```
GET /api/admin/reports/ordenes/excel
GET /api/admin/reports/ordenes/pdf
```

#### Parameters

| Parameter | Type | Required | Default | Constraints |
|-----------|------|----------|---------|-------------|
| `status` | `OrderStatus` string | NO | — | Must be `PENDIENTE`, `CONFIRMADA`, or `CANCELADA` if provided |
| `desde` | `yyyy-MM-dd` | NO | — | If provided, `hasta` must also be present; `desde` ≤ `hasta` |
| `hasta` | `yyyy-MM-dd` | NO | — | If provided, `desde` must also be present |
| `userId` | Long | NO | — | If provided, must be a positive integer |

**All filters are optional.** When none are provided the export covers all orders in the
system, all statuses, all dates, all users.

**No pagination on the export.** The endpoint returns ALL matching orders (not a page). The
document may be large at production scale; at academic scale (tens to hundreds of rows) this
is acceptable.

#### Document content

**Header:**
- Title: "Listado de Órdenes"
- Applied filters summary (e.g. "Estado: CONFIRMADA | Desde: 01/01/2026 | Hasta: 31/01/2026")
- Total number of matching orders

**Table columns:** `ID Orden`, `Fecha`, `Usuario (email)`, `Estado`, `Total (S/)`

Rows ordered by `order_date` descending (most recent first).

#### Validation errors → HTTP 400

- `status` present but not a valid `OrderStatus` value (case-insensitive matching is
  allowed for the three values; anything else → 400)
- `desde` present without `hasta`, or vice versa
- `desde` / `hasta` in wrong format
- `desde` after `hasta`
- `userId` present but not a positive integer

#### Scenarios

**SCENARIO RPT-04-01 — Happy path, all filters, Excel**
```
GIVEN  an authenticated ADMIN
  AND  multiple orders exist across users, statuses, and dates
WHEN   GET /api/admin/reports/ordenes/excel?status=CONFIRMADA&desde=2026-01-01&hasta=2026-01-31&userId=2
THEN   HTTP 200
  AND  Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  AND  Content-Disposition: attachment; filename="ordenes_2026-01-01_2026-01-31.xlsx"
  AND  all rows have status=CONFIRMADA, order_date in range, user_id=2
  AND  no rows violating any applied filter appear
```

**SCENARIO RPT-04-02 — No filters → all orders, PDF**
```
GIVEN  an authenticated ADMIN
  AND  N orders exist in total
WHEN   GET /api/admin/reports/ordenes/pdf
THEN   HTTP 200
  AND  Content-Type: application/pdf
  AND  Content-Disposition: attachment; filename="ordenes_todos.pdf"
  AND  document contains N rows (all orders)
```

**SCENARIO RPT-04-03 — Empty result → 200 with valid document**
```
GIVEN  an authenticated ADMIN
  AND  no orders matching the filters exist
WHEN   GET /api/admin/reports/ordenes/excel?status=CANCELADA&desde=2020-01-01&hasta=2020-01-01
THEN   HTTP 200
  AND  workbook is valid; table has 0 data rows
  AND  header summary shows 0 total orders
```

**SCENARIO RPT-04-04 — Validation: invalid status string → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ordenes/excel?status=SHIPPED
THEN   HTTP 400
  AND  message lists valid values: PENDIENTE, CONFIRMADA, CANCELADA
```

**SCENARIO RPT-04-05 — Validation: desde without hasta → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ordenes/excel?desde=2026-01-01
THEN   HTTP 400
```

**SCENARIO RPT-04-06 — Validation: desde after hasta → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ordenes/excel?desde=2026-02-01&hasta=2026-01-01
THEN   HTTP 400
```

**SCENARIO RPT-04-07 — Validation: userId = -1 → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ordenes/excel?userId=-1
THEN   HTTP 400
```

**SCENARIO RPT-04-08 — Auth: CLIENTE → 403**
```
GIVEN  an authenticated CLIENTE
WHEN   GET /api/admin/reports/ordenes/excel
THEN   HTTP 403
```

---

### REQ-RPT-05 — HTTP response contract (all 8 endpoints)

All 8 report endpoints MUST conform to the following response contract.

#### Content-Type per format

| Format | Content-Type |
|--------|-------------|
| Excel | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| PDF | `application/pdf` |

#### Content-Disposition

Every successful response (HTTP 200) MUST include:
```
Content-Disposition: attachment; filename="{report}_{params}.{ext}"
```

Filename convention per report:

| Report | Excel filename | PDF filename |
|--------|---------------|--------------|
| R1 (with dates) | `ventas_{desde}_{hasta}.xlsx` | `ventas_{desde}_{hasta}.pdf` |
| R2 (with dates) | `productos_vendidos_{desde}_{hasta}.xlsx` | `productos_vendidos_{desde}_{hasta}.pdf` |
| R2 (no dates) | `productos_vendidos_todos.xlsx` | `productos_vendidos_todos.pdf` |
| R3 (with dates) | `kardex_{productId}_{desde}_{hasta}.xlsx` | `kardex_{productId}_{desde}_{hasta}.pdf` |
| R3 (no dates) | `kardex_{productId}_todos.xlsx` | `kardex_{productId}_todos.pdf` |
| R4 (with dates) | `ordenes_{desde}_{hasta}.xlsx` | `ordenes_{desde}_{hasta}.pdf` |
| R4 (no dates) | `ordenes_todos.xlsx` | `ordenes_todos.pdf` |

#### Empty-result behavior (locked)

When the query returns zero rows, the system MUST return HTTP **200** with a valid, parseable
document containing the header/summary section but zero data rows. HTTP 204 is NOT permitted.
The Excel workbook MUST be openable by standard spreadsheet applications. The PDF MUST start
with `%PDF`.

#### Scenarios

**SCENARIO RPT-05-01 — Excel document is parseable**
```
GIVEN  a successful Excel endpoint response
WHEN   the response body bytes are parsed with XSSFWorkbook(ByteArrayInputStream)
THEN   no exception is thrown
  AND  the workbook contains at least one sheet
  AND  row 0 (header row) is not empty
```

**SCENARIO RPT-05-02 — PDF document has magic bytes**
```
GIVEN  a successful PDF endpoint response
WHEN   the first 4 bytes of the response body are read
THEN   they equal 0x25 0x50 0x44 0x46 (i.e. "%PDF")
```

**SCENARIO RPT-05-03 — No 204 is ever returned**
```
GIVEN  an authenticated ADMIN
  AND  a filter that matches zero records
WHEN   any of the 8 report endpoints is called
THEN   HTTP status is 200 (never 204)
```

---

### REQ-RPT-06 — Authorization

All 8 endpoints MUST require the caller to be authenticated with role `ADMIN`. This is
enforced by the existing `SecurityConfig` rule `hasRole("ADMIN")` on `/api/admin/**`.

| Caller | Expected HTTP status |
|--------|---------------------|
| Authenticated ADMIN | 200 (or 400/404 for validation failures) |
| Authenticated CLIENTE | 403 |
| Unauthenticated (no token) | 401 |

The spec does NOT change `SecurityConfig`. The `/api/admin/**` catch-all already covers
`/api/admin/reports/**`.

#### Scenarios

**SCENARIO RPT-06-01 — Unauthenticated request → 401**
```
GIVEN  no Authorization header
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-01-01&hasta=2026-01-31
THEN   HTTP 401
```

**SCENARIO RPT-06-02 — CLIENTE token → 403**
```
GIVEN  a valid JWT for a user with role CLIENTE
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-01-01&hasta=2026-01-31
THEN   HTTP 403
```

**SCENARIO RPT-06-03 — ADMIN token → proceeds to business logic**
```
GIVEN  a valid JWT for a user with role ADMIN
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-01-01&hasta=2026-01-31
THEN   HTTP is NOT 401 and NOT 403 (200 or validation error)
```

---

### REQ-RPT-07 — Parameter validation contract (shared across all reports)

All 8 endpoints MUST validate input parameters before invoking any service logic. Invalid
parameters MUST return HTTP **400** with a JSON body containing a human-readable `error`
field describing what is wrong.

**Date format.** The only accepted date input format is `yyyy-MM-dd` (ISO 8601 local date).
Any other format (e.g. `01/01/2026`, `2026-1-1`, `20260101`) MUST result in HTTP 400.

**Date range consistency.** When both `desde` and `hasta` are present, `desde` MUST NOT be
strictly after `hasta`. A same-day range (`desde == hasta`) is valid and means a single
calendar day.

**Lima timezone mapping.** `desde` maps to `00:00:00.000000000` in `America/Lima`;
`hasta` maps to `23:59:59.999999999` in `America/Lima`. These boundaries are converted to UTC
`Instant` before being passed to repository queries.

**Numeric params** (`limit`, `userId`, `productId`) MUST be positive integers when provided.
Values ≤ 0 or non-numeric strings → 400.

**Enum params** (`status`, `granularidad`). Case-insensitive matching is RECOMMENDED for
robustness, but implementations MAY choose case-sensitive. The spec requires that invalid
values always produce HTTP 400 with a list of valid options in the error message.

#### Scenario

**SCENARIO RPT-07-01 — Invalid date format → 400**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ventas/excel?desde=01-01-2026&hasta=31-01-2026
THEN   HTTP 400
  AND  message mentions the expected format yyyy-MM-dd
```

**SCENARIO RPT-07-02 — Same-day range is valid**
```
GIVEN  an authenticated ADMIN
WHEN   GET /api/admin/reports/ventas/excel?desde=2026-01-15&hasta=2026-01-15
THEN   HTTP 200 (not 400)
```

---

### REQ-RPT-08 — No side effects (read-only guarantee)

The `reporting` capability MUST NOT:
- Execute any INSERT, UPDATE, or DELETE SQL statement
- Trigger any write to `orders`, `order_items`, `products`, `stock_movement`, `users`, or
  `categories` tables
- Create new Flyway migrations (schema is untouched)
- Modify any existing `Service` interface (specifically: `OrderService` contract is frozen)

Additive repository changes (`JpaSpecificationExecutor<Order>`, new `@Query` methods,
`OrderSpecification`) are permitted because they are strictly additive and do not alter
existing method signatures.

---

## What is explicitly OUT OF SCOPE

The following are NOT required by this spec and MUST NOT be implemented as part of this
change:

- All-products inventory snapshot (R3 requires `productId`; global catalog export is future)
- Scheduled or emailed report delivery
- Streaming generation (`SXSSFWorkbook`) — in-memory only at academic scale
- Additional formats: CSV, JSON download, HTML rendering
- Charts, graphics, branding templates inside documents
- Front-end dashboard consuming these endpoints
- Modifying the `OrderService` interface or its existing implementations
- Any new Flyway migration (V5 does NOT exist)
- OpenPDF 2.1.x/3.x upgrade (blocked on Java 21 migration)
- Pagination of report results (exports are full-result, not paged)

---

## Invariants

1. Every 200 response body MUST be a valid, non-empty binary document of the declared
   Content-Type.
2. Every 200 response MUST include `Content-Disposition: attachment`.
3. CONFIRMADA-status filtering in R1 and R2 is non-negotiable — a calculation that includes
   PENDIENTE or CANCELADA orders is incorrect.
4. Date grouping in R1 MUST use Lima local time via `AT TIME ZONE 'America/Lima'`.
5. R3 MUST always include the `products.stock` header value even when the movement history
   is empty.
6. HTTP 204 is forbidden for all 8 endpoints.
7. `OrderService` contract is frozen — no method signatures on that interface change.
8. No write operations are performed by any class in the `reporting` capability.

---

## Open questions resolved in this spec

| # | Question | Resolution |
|---|----------|------------|
| R1 granularidad default | What is the default? | `dia`. Values: `dia` \| `mes` only. |
| R1 desde/hasta required? | Required or optional? | **Required** for R1. |
| R2 limit default & max | What are the bounds? | Default `10`, max `100`, min `1`. |
| R2 date range | Required or optional? | **Optional**. Both must be present or both absent. |
| R3 desde/hasta | Required or optional? | **Optional**. Both must be present or both absent. |
| R3 productId not found | Behavior? | HTTP **404** with message. |
| R4 filters | Required or optional? | All **optional**. |
| R4 status validation | Behavior on invalid value? | HTTP **400** listing valid values. |
| Empty result behavior | 200 or 204? | **HTTP 200** with valid empty document (locked). |
| Lima timezone mapping | Exact mapping? | desde → 00:00:00 Lima; hasta → 23:59:59.999... Lima; converted to UTC Instant for queries. |

---

*Spec archived from change `reportes` on 2026-06-09. Ready for production.*
