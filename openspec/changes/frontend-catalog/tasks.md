# Tasks: Frontend Catalog — Real Product Catalog UI

> SDD TASKS phase. Sources: spec #364, design #365 + `design.md` (authoritative). Artifact store: hybrid. Date: 2026-06-16.
> Execution order: P0 → P1 → P2 (strict RED-GREEN) → P3 ∥ P4 (independent after P2) → P5 (acceptance/deferred).
> STRICT TDD phases: **P2 (ProductService only)**. Pragmatic render-tests: P3, P4.

---

## Phase 0 — AuthService URL Alignment (bug fix prerequisite)

> Sequential — must complete before any new service work. Existing auth specs must remain GREEN after this fix.
> This is NOT new feature work — it is a latent foundation bug: relative URLs in the absence of `proxy.conf.json` hit `:4200` not `:8080`.

### [x] P0.1 — Prefix AuthService URLs with `environment.apiBaseUrl`
**Spec**: N/A (latent foundation bug)
**Design**: URL strategy section — absolute URLs `${environment.apiBaseUrl}/api/...`
**Files**: `frontend/src/app/core/auth/auth.service.ts`
**Action**: Import `environment` from `../../environments/environment`. Change:
- `LOGIN_URL = '/api/auth/login'` → `` LOGIN_URL = `${environment.apiBaseUrl}/api/auth/login` ``
- `REGISTER_URL = '/api/auth/register'` → `` REGISTER_URL = `${environment.apiBaseUrl}/api/auth/register` ``

**Done when**: `auth.service.ts` uses absolute URLs; `tsc --noEmit` passes.
**Depends on**: nothing (first task).

### [x] P0.2 — Update `auth.service.spec.ts` expectOne assertions to absolute URLs
**Spec**: N/A (test alignment to P0.1)
**Design**: URL strategy — spec assertions must match absolute-URL implementation
**Files**: `frontend/src/app/core/auth/auth.service.spec.ts`
**Action**: Replace every `expectOne('/api/auth/login')` with `expectOne('http://localhost:8080/api/auth/login')` and every `expectOne('/api/auth/register')` with `expectOne('http://localhost:8080/api/auth/register')`. Update any matching `expectNone(...)` calls similarly.
**Done when**: `npm test` exits 0 — all pre-existing auth specs pass with absolute-URL assertions (GREEN gate).
**Depends on**: P0.1.

---

## Phase 1 — Model Additions

> Pure TypeScript additions. Both tasks can run in parallel once P0 completes.

### [x] P1.1 — Add `CatalogFilter` interface and `PLACEHOLDER_IMAGE` constant to `product.model.ts`
**Spec**: ProductService Signal Contract; product-catalog capability (design D4)
**Design**: Decision 4; imageUrl null fallback section
**Files**: `frontend/src/app/models/product.model.ts`
**Action**: Append to the file (do NOT modify existing interfaces):
```ts
/** Client-side filter state for the product catalog search. */
export interface CatalogFilter {
  name?: string;        // free-text, debounced; omitted from params when empty
  categoryId?: number;  // from the categories dropdown; omitted when unset
  priceMin?: number;    // omitted when undefined
  priceMax?: number;    // omitted when undefined
}

/** Fallback image when ProductResponse.imageUrl is null. Single source of truth. */
export const PLACEHOLDER_IMAGE = 'assets/placeholder-product.svg';
```
**Done when**: `tsc --noEmit` clean; both exports importable from the models path.
**Depends on**: P0.2 (suite clean before extending models).

### [x] P1.2 — Create `assets/placeholder-product.svg`
**Spec**: imageUrl Null Fallback — null → visible placeholder rendered, not broken `<img>`
**Design**: imageUrl null fallback section — static SVG shipped with this change
**Files**: `frontend/src/assets/placeholder-product.svg`
**Action**: Create a minimal valid inline SVG (e.g. grey rectangle with centred "No Image" text). Must render correctly when used as an `<img src="...">` target.
**Done when**: File exists at the correct assets path; `ng build` copies it to dist without error.
**Depends on**: P0.2.
**Parallel with**: P1.1.

---

## Phase 2 — ProductService (STRICT RED-GREEN)

> **STRICT TDD**: write the FAILING spec first → run `npm test` to confirm RED → then implement to GREEN.
> No skipping the RED confirmation step. This is the sole strict-TDD surface for this change.

### [x] P2.1 — `ProductService` spec RED (write failing tests first)
**Spec**: ProductService Signal Contract (4 scenarios); Paginated Product Grid; Product Filtering; Product Detail View; Error State
**Design**: ProductService API; Test Plan — Strict RED-GREEN; `buildSearchParams`; category cache
**Files**: `frontend/src/app/features/catalog/product.service.spec.ts` (CREATE)
**Action**: Write ALL tests using `HttpTestingController` (mirror `auth.service.spec.ts` style). The following must be covered — ALL must fail before implementation:

1. `search()` issues `GET http://localhost:8080/api/products` (absolute URL — method + URL assertion).
2. **Params casing** — `req.request.params.get('page')` and `'size'` exist; `size === '12'`; keys are lowercase.
3. **Filter omission** — empty filter `{}`: `params.has('name') === false`; no `categoryId`, `priceMin`, `priceMax` keys.
4. **Filter composition** — full `CatalogFilter`: all 4 keys present as correct strings; whitespace-only `name` omitted (`.trim()` guard).
5. **Signal population** — `flush(pageResponse)`: `products()` / `totalPages()` / `totalElements()` reflect body; `loading()` true mid-flight, false after flush.
6. **Error path** — 5xx flush: `loading()` false, `error()` non-null, `products()` empty/unchanged.
7. `getById(id)` GETs `http://localhost:8080/api/products/{id}`; success emits the product; 404 flush surfaces error to subscriber.
8. **Category cache** — first `listCategories()` → exactly one GET `http://localhost:8080/api/categories`; second subscription → `http.expectNone(...)` (proves `shareReplay(1)`).

**Done when**: `npm test` exits with ALL new `product.service` tests in FAILED state (RED). Pre-existing tests still pass.
**Depends on**: P1.1 (`CatalogFilter` type needed in spec file).

### [x] P2.2 — `ProductService` implementation GREEN
**Spec**: ProductService Signal Contract (all); Paginated Product Grid; Product Filtering; Product Detail View; Error State
**Design**: ProductService API (signals, methods, `buildSearchParams`, `shareReplay` categories); State Model section
**Files**: `frontend/src/app/features/catalog/product.service.ts` (CREATE)
**Action**: Implement `@Injectable({ providedIn: 'root' })` `ProductService`:
- `private readonly DEFAULT_PAGE_SIZE = 12`
- Private writable signals: `_products signal<ProductResponse[]>([])`, `_totalPages signal<number>(0)`, `_totalElements signal<number>(0)`, `_loading signal<boolean>(false)`, `_error signal<string | null>(null)` — each exposed `.asReadonly()`.
- `search(filter: CatalogFilter, page: number): void` — calls `buildSearchParams`, sets `_loading(true)`, GETs `${environment.apiBaseUrl}/api/products`, on success populates products/totalPages/totalElements + loading(false); catchError sets `_error` + `_loading(false)`.
- `private buildSearchParams(filter: CatalogFilter, page: number): HttpParams` — exact lowercase keys; omit nullish/empty values (see design for exact logic).
- `getById(id: number): Observable<ProductResponse>` — GETs `${environment.apiBaseUrl}/api/products/${id}`.
- `listCategories(): Observable<CategoryResponse[]>` — lazy `private categories$?` with `shareReplay(1)`, GETs `${environment.apiBaseUrl}/api/categories`.

**Done when**: `npm test` passes ALL tests from P2.1 (GREEN). Gate: `npm test` exits 0.
**Depends on**: P2.1.

---

## Phase 3 — Presentational Components (parallel with Phase 4 after P2)

> Pragmatic render tests (critical branches only). No strict RED-GREEN cycle.

### [x] P3.1 — `ProductCardComponent` + pragmatic spec
**Spec**: imageUrl Null Fallback (2 scenarios); Route Registration (routerLink)
**Design**: Decision 3; Component Tree; imageUrl null section
**Files**:
- `frontend/src/app/features/catalog/product-card.component.ts` (CREATE)
- `frontend/src/app/features/catalog/product-card.component.spec.ts` (CREATE)

**Action**:
1. Standalone `ProductCardComponent` (`app-product-card`): `@Input() product!: ProductResponse`. Root element is `<a [routerLink]="['/catalogo', product.id]">` (no `Router` injection — template concern). Image: `<img [src]="product.imageUrl ?? PLACEHOLDER_IMAGE" [alt]="product.name">`. Renders name and price.
2. Spec (pragmatic render): `imageUrl: null` → `<img>` src equals `PLACEHOLDER_IMAGE`, NOT `'null'`; `routerLink` attribute targets `/catalogo/{id}`; name is displayed in the rendered DOM.

**Done when**: `npm test` passes card spec. No strict RED-GREEN sequence required.
**Depends on**: P2.2 (ProductResponse shape confirmed green).
**Parallel with**: P3.2.

### [x] P3.2 — `CatalogFilterComponent` + pragmatic spec
**Spec**: Product Filtering — debounceTime(300ms), category dropdown, price range, filters compose
**Design**: Decision 2; Component Tree; Debounce section
**Files**:
- `frontend/src/app/features/catalog/catalog-filter.component.ts` (CREATE)
- `frontend/src/app/features/catalog/catalog-filter.component.spec.ts` (CREATE)

**Action**:
1. Standalone `CatalogFilterComponent` (`app-catalog-filter`): `@Input() categories: CategoryResponse[] = []`, `@Output() filterChange = new EventEmitter<CatalogFilter>()`. Owns `debounceTime(300) + distinctUntilChanged()` on the name input only (via `FormControl.valueChanges` or a `Subject`). Category select and price inputs emit `filterChange` immediately. Always emits a fully composed `CatalogFilter` object.
2. Spec (pragmatic, `fakeAsync`): rapid name keystrokes → single `filterChange` emission after `tick(300)`; category select → immediate emission with `categoryId`; both fields set → composed `CatalogFilter` includes all set fields.

**Done when**: `npm test` passes filter spec.
**Depends on**: P2.2.
**Parallel with**: P3.1.

---

## Phase 4 — Container + Detail + Routing (parallel with Phase 3 after P2)

> Pragmatic render tests. `CatalogComponent` replaced in-place — export name `CatalogComponent` **MUST NOT change**.

### [x] P4.1 — `CatalogComponent` smart container (replace stub in-place)
**Spec**: Paginated Product Grid (all 4 scenarios); Product Filtering (all 6); Loading State; Empty State; Error State; Route Registration (export name unchanged)
**Design**: Component Tree; State Model; Refetch flow; Error/Empty/Loading states
**Files**: `frontend/src/app/features/catalog/catalog.component.ts` (MODIFY in-place — keep export name)

**Action**: Replace stub body with full smart container. `export class CatalogComponent` stays unchanged.
- `inject(ProductService)`.
- Signals: `filter = signal<CatalogFilter>({})`, `page = signal<number>(0)`, `categories = signal<CategoryResponse[]>([])`.
- `ngOnInit`: subscribe `productService.listCategories()` → set categories; call `runSearch()`.
- `runSearch()`: calls `productService.search(filter(), page())`.
- `onFilterChange(f: CatalogFilter)`: set filter, reset page to 0, `runSearch()`.
- `onPageChange(n: number)`: set page to n, `runSearch()`.
- Template covers: `@if (productService.loading())` spinner; `@else if (!products.length && !error)` empty state "No se encontraron productos"; `@else if (error)` error block; `@for` grid of `<app-product-card [product]="p">`;`<app-catalog-filter [categories]="categories()" (filterChange)="onFilterChange($event)">`;pagination buttons driven by `productService.totalPages()`.

**Done when**: Component compiles; existing `/catalogo` route still resolves `CatalogComponent` by name; `tsc --noEmit` clean.
**Depends on**: P3.1, P3.2 (child component selectors must exist to compile template).

### [x] P4.2 — `ProductDetailComponent` + pragmatic spec
**Spec**: Product Detail View (all 3 scenarios); Loading State — detail; Error State — 404 + 5xx on detail
**Design**: Routing section; ProductDetailComponent description; Error/Empty/Loading states table
**Files**:
- `frontend/src/app/features/catalog/product-detail.component.ts` (CREATE)
- `frontend/src/app/features/catalog/product-detail.component.spec.ts` (CREATE)

**Action**:
1. Standalone `ProductDetailComponent` (`app-product-detail`): injects `ProductService` + `ActivatedRoute`. `ngOnInit`: `const id = Number(route.snapshot.paramMap.get('id'))`. Validate `isFinite(id)` before HTTP — non-numeric id shows not-found view without making a call. `getById(id).subscribe({ next: set product, error: handle })`. `error` handler: `err.status === 404` → "Producto no encontrado" + `NotificationService.notify('Producto no encontrado', 'error')`; else generic + `notify('No se pudo cargar el producto', 'error')`. Template: loading state "Cargando..."; loaded state (name, price, description, categoryName, stock, `imageUrl ?? PLACEHOLDER_IMAGE`); 404 state with back link to `/catalogo`; generic error state.
2. Spec (pragmatic): loaded branch renders product name/price; 404 branch renders "Producto no encontrado" and calls `NotificationService.notify`; back link present on 404 branch. Mock `ActivatedRoute` + `ProductService.getById`.

**Done when**: `npm test` passes detail spec.
**Depends on**: P2.2 (`ProductService.getById`).
**Parallel with**: P4.1 (independent file — P4.1 and P4.2 both depend on P2.2; P4.2 does NOT depend on P3.x).

### [x] P4.3 — Add `catalogo/:id` lazy route to `app.routes.ts`
**Spec**: Route Registration — `/catalogo/:id` lazy-loaded; `CatalogComponent` export name unchanged
**Design**: Routing section — sibling lazy route added immediately after the `catalogo` route entry
**Files**: `frontend/src/app/app.routes.ts` (MODIFY)
**Action**: Insert after the `{ path: 'catalogo', loadComponent: ... }` block:
```ts
{
  path: 'catalogo/:id',
  loadComponent: () =>
    import('./features/catalog/product-detail.component').then((m) => m.ProductDetailComponent),
},
```
**Done when**: `tsc --noEmit` clean; Angular router resolves `/catalogo/1` to `ProductDetailComponent`.
**Depends on**: P4.2 (`ProductDetailComponent` file must exist for the import to type-check).

### [x] P4.4 — `CatalogComponent` pragmatic render spec (optional)
**Spec**: Paginated Product Grid — initial load; Loading State (list)
**Design**: Test Plan — catalog container OPTIONAL light render
**Files**: `frontend/src/app/features/catalog/catalog.component.spec.ts` (CREATE)
**Action**: Light render test: mock `ProductService` with stub readonly signals; confirm `ngOnInit` triggers `search()` once; confirm `@for` grid renders product cards from mocked `products()` signal. NOT exhaustive — the service logic is fully covered by P2.
**Done when**: `npm test` exits 0 including this spec.
**Depends on**: P4.1.
**Note**: Optional — ship after P4.1–P4.3 are confirmed green.

---

## Phase 5 — Acceptance (deferred / live — not blocking automated tasks)

> Run after all automated phases complete. Both servers required: backend on `:8080`, frontend on `:4200`.

### [ ] P5.1 — Full automated test suite green
**Action**: `cd frontend && npm test`. Confirm ALL specs pass: auth suite (absolute URLs), product.service spec (all 8 scenario groups), product-card, catalog-filter, product-detail, optional catalog container spec.
**Done when**: `npm test` exits 0, zero failures.

### [ ] P5.2 — Live smoke: catalog list loads from backend
**Action**: Start both servers. Navigate to `http://localhost:4200/catalogo`. Network tab must show `GET http://localhost:8080/api/products?page=0&size=12` returning 200 with correct CORS headers. Loading indicator appears then disappears. Pagination buttons match `totalPages`.
**Done when**: No CORS errors; products grid renders real data.

### [ ] P5.3 — Live smoke: filters work end-to-end
**Action**: Type in name filter box — confirm single request fires after 300ms (debounce). Select category — confirm `categoryId` param in request and page resets to 0.
**Done when**: Network tab confirms debounced single request with correct lowercase params.

### [ ] P5.4 — Live smoke: product detail and 404 handling
**Action**: Click a product card → `/catalogo/{id}` loads with all fields rendered. Navigate to `/catalogo/99999` → "Producto no encontrado" message + notification appear; no blank page.
**Done when**: Both happy-path and 404 branches confirmed in browser.

### [ ] P5.5 — Live smoke: null imageUrl placeholder renders
**Action**: Find or temporarily set a product with `imageUrl: null`. Confirm placeholder SVG renders — not a broken image or `src="null"`.
**Done when**: Placeholder visible for null-imageUrl product; no broken image icon in browser.

---

## Dependency Graph

```
P0.1 → P0.2
P0.2 → P1.1
P0.2 → P1.2               (parallel with P1.1)
P1.1 → P2.1
P2.1 → P2.2
P2.2 → P3.1               (parallel with P3.2 and P4.2)
P2.2 → P3.2
P2.2 → P4.2
P3.1 + P3.2 → P4.1
P4.2 → P4.3
P4.1 → P4.4               (optional)
P4.3 [+ P4.4 opt.] → P5.1
P5.1 → P5.2 .. P5.5       (live smokes independent of each other)
```

## Parallelism Map

| Can run in parallel | Condition |
|---------------------|-----------|
| P1.1 and P1.2 | After P0.2 |
| P3.1 and P3.2 | After P2.2 |
| P4.1 and P4.2 | After P3.1+P3.2 done (P4.2 needs only P2.2 — can start with P4.1) |
| P5.2 through P5.5 | After P5.1 (live smokes are mutually independent) |

**Total tasks: 18** (P0: 2, P1: 2, P2: 2, P3: 2, P4: 4 [including 1 optional], P5: 5, plus P4.4 optional = 18 total).
