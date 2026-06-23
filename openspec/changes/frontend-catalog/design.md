# Design: Frontend Catalog — Real Product Catalog UI

> SDD DESIGN phase. Source: proposal #363 / `proposal.md`, explore #362 / `explore.md`. Artifact store: hybrid. Date: 2026-06-16.
> Resolves the 4 open decisions left by the proposal. The 2 forks already decided by PROPOSE (service location, pagination style) are honored, not re-litigated.

## Technical Approach

Container–presentational split inside `features/catalog/`. One smart container (`CatalogComponent`)
owns ALL state and the single `ProductService`; presentational children (`product-card`,
`catalog-filter`) are dumb, `@Input()`/`@Output()`-only, no service injection. `ProductService`
mirrors the `AuthService` shape one-to-one: private writable signals exposed `.asReadonly()`,
`computed()` derived state, `inject(HttpClient)`, **absolute URLs prefixed with `environment.apiBaseUrl`** (e.g.
`${environment.apiBaseUrl}/api/products`). Server-side pagination
via Spring `page`/`size`; filters compose into `HttpParams` with empty values omitted. The product
detail is a routed sibling at `/catalogo/:id` that reads the param and calls `getById`. Strict
RED-GREEN Jest on the service HTTP/params logic; pragmatic render tests on the three components.

### URL strategy (DECISION — overrides the design's first draft; see engram #366)

**ABSOLUTE URLs prefixed with `environment.apiBaseUrl`.** Every service HTTP call builds its URL as
`${environment.apiBaseUrl}/api/...` (e.g. `http://localhost:8080/api/products`). Import `environment`
directly (as `app.config.ts:6` already does), not the unused `API_BASE_URL` re-export.

**Why this overrides the first draft.** The first draft proposed relative URLs "to match the
foundation". That was WRONG — it would have propagated a **latent foundation bug**:
- `AuthService` (`auth.service.ts:37`) uses relative URLs (`/api/auth/login`), but there is **no
  `proxy.conf.json`**. Under `ng serve` on `:4200`, a relative request hits
  `http://localhost:4200/api/...` → 404 from the Angular dev server; it NEVER reaches the backend on
  `:8080`. The foundation's login would NOT work in a live dev session — it only "passes" because the
  unit tests mock HTTP. This is exactly the gap the (deferred) Phase 6 smoke would have exposed.
- The backend `CorsConfig` allowlist (`localhost:4200 → :8080`) ONLY makes sense for **cross-origin
  (absolute)** requests. Relative URLs make CORS dead weight. Absolute URLs are the ONLY option
  consistent with the CORS decision already shipped in the foundation.

**Blanket rule for APPLY**: anywhere this design (or a spec) shows a bare `/api/...` string in a
service HTTP call or a `HttpTestingController.expectOne(...)` assertion, prefix it with
`environment.apiBaseUrl`. The interceptor matcher is unaffected — `req.url.includes('/api/auth/')`
still matches an absolute URL like `http://localhost:8080/api/auth/login`.

**AuthService alignment (folded into THIS change — it's a bug fix, not auth-feature work)**:
- `frontend/src/app/core/auth/auth.service.ts` — prefix `LOGIN_URL`/`REGISTER_URL` with
  `environment.apiBaseUrl`.
- `frontend/src/app/core/auth/auth.service.spec.ts` — update the `expectOne('/api/auth/...')`
  assertions to the absolute URL.

In production, `environment.prod.ts`'s `apiBaseUrl` points to the real backend origin — no code change.

## Resolved Decisions (the 4 open questions)

### Decision 1 — Default page `size`: **12, NOT user-adjustable (this change)**

| Option | Tradeoff | Verdict |
|--------|----------|---------|
| **size = 12, fixed** | Clean 2/3/4-col grid (12 = 2·3·4·6); single `signal<number>` constant; deep-linkable; trivial to test | **CHOOSE** |
| size = 20, fixed | More per page, fewer clicks | Reject — uneven across common column counts; no academic gain |
| User-adjustable size selector | "Items per page" control | Reject for now — adds a control + state + spec surface for marginal value; defer to v2 |

`size` is a private readonly constant in `ProductService` (`DEFAULT_PAGE_SIZE = 12`). It is sent as a
literal `HttpParam` on every search so we never rely on Spring's silent default (mitigates explore
risk #2). `page` is the only paging state that changes (a `signal<number>`, zero-based to match
`PageResponse.page`). A size selector is explicitly out of scope; revisit in a catalog v2.

### Decision 2 — `catalog-filter`: **its own presentational sub-component** (`catalog-filter.component.ts`)

| Option | Tradeoff | Verdict |
|--------|----------|---------|
| **Dedicated `CatalogFilterComponent`** | Keeps container lean; filter markup + debounce wiring isolated; independently render-testable; screaming-arch consistent | **CHOOSE** |
| Inline markup in `CatalogComponent` | One fewer file | Reject — bloats the container with form markup + name-debounce plumbing, mixing presentation with orchestration |

`CatalogFilterComponent` is dumb: it takes `@Input() categories: CategoryResponse[]` and emits a
single `@Output() filterChange = new EventEmitter<CatalogFilter>()`. The **debounce on the name input
lives inside the filter component** (it owns the input event), so the container receives already-debounced,
composed filter objects — the container stays a pure orchestrator. The filter does NOT call the service
and does NOT know about pagination.

### Decision 3 — `product-card` boundary: **`@Input()`-only, navigation owned by the container**

| Option | Tradeoff | Verdict |
|--------|----------|---------|
| **`@Input() product` only; card emits/links, container routes** | Pure presentational; reusable (cart/admin later); render-test in isolation with a stub product; no `Router` coupling | **CHOOSE** |
| Card owns its own `routerLink`/`Router.navigate` | Self-contained | Reject — couples a leaf presentational component to routing; harder to reuse and to unit-test |

`ProductCardComponent` takes `@Input() product: ProductResponse` and nothing else. Navigation to detail
is expressed declaratively: the card's root is an `<a [routerLink]="['/catalog', product.id]">` —
a `routerLink` is a TEMPLATE concern (no `Router` injection, still trivially render-testable), so the
card stays presentational while the route target stays a single source of truth. The card does NOT
inject any service. `imageUrl` null is handled here via the shared placeholder (see below).

### Decision 4 — `CatalogFilter` interface: **YES, add it to `product.model.ts`**

The filter signal shape is a real contract shared by `CatalogComponent` (state) and
`CatalogFilterComponent` (`@Output`), and it drives `HttpParams` construction in `ProductService`.
A named interface beats an inline object literal repeated in three places. Add to `product.model.ts`
(alongside the other DTO mirrors — consistent with the foundation's flat `models/` decision):

```ts
/** Client-side filter state for the product catalog search. */
export interface CatalogFilter {
  name?: string;        // free-text, debounced; omitted from params when empty
  categoryId?: number;  // from the categories dropdown; omitted when unset
  priceMin?: number;    // omitted when undefined
  priceMax?: number;    // omitted when undefined
}
```

All fields optional — an empty `{}` means "no filters", which maps to a params object carrying only
`page` + `size`. This single type is the contract for the `@Output`, the container signal, and the
service method argument.

## Component Tree (container–presentational)

```
/catalogo                        CatalogComponent           (SMART container)
                                   ├── injects ProductService
                                   ├── owns: filter signal, page signal
                                   ├── reads: products(), totalPages(), loading(), error() (from service)
                                   │
                                   ├── <app-catalog-filter           (DUMB)
                                   │      [categories]="categories()"
                                   │      (filterChange)="onFilterChange($event)">
                                   │        └─ owns name-input debounceTime(300) internally
                                   │
                                   ├── @for product of products()      (grid)
                                   │      <app-product-card [product]="product"/>   (DUMB)
                                   │          └─ <a [routerLink]="['/catalogo', product.id]">
                                   │          └─ <img [src]="product.imageUrl ?? PLACEHOLDER_IMAGE">
                                   │
                                   └── pagination buttons → onPageChange(n)

/catalogo/:id                    ProductDetailComponent     (SMART, routed sibling)
                                   ├── injects ProductService + ActivatedRoute
                                   ├── reads :id param, calls getById(id)
                                   └── loading / loaded / 404 states
```

Boundary rule: only `CatalogComponent` and `ProductDetailComponent` inject services. `product-card`
and `catalog-filter` receive everything via `@Input()` and report via `@Output()`. This keeps the two
presentational components pure (render-testable with plain inputs, zero HTTP).

## ProductService API

`@Injectable({ providedIn: 'root' })`, `private readonly http = inject(HttpClient)` (or constructor
injection — match AuthService's constructor style). State signals mirror `AuthService`.

### State (signals)

```ts
private readonly DEFAULT_PAGE_SIZE = 12;

private readonly _products = signal<ProductResponse[]>([]);
private readonly _totalPages = signal<number>(0);
private readonly _totalElements = signal<number>(0);
private readonly _loading = signal<boolean>(false);
private readonly _error = signal<string | null>(null);

readonly products = this._products.asReadonly();
readonly totalPages = this._totalPages.asReadonly();
readonly totalElements = this._totalElements.asReadonly();
readonly loading = this._loading.asReadonly();
readonly error = this._error.asReadonly();
```

### Methods

```ts
/**
 * Server-side product search. Builds HttpParams (page/size always; filter
 * params only when present), sets loading/error signals, populates the
 * products + totalPages signals from the PageResponse on success.
 * page is ZERO-BASED (matches PageResponse.page).
 */
search(filter: CatalogFilter, page: number): void;   // size is the internal constant

/** GET /api/products/{id} → ProductResponse. Caller (detail component) subscribes. */
getById(id: number): Observable<ProductResponse>;

/** GET /api/categories → CategoryResponse[], cold-loaded once, cached. */
listCategories(): Observable<CategoryResponse[]>;
```

**Return-type rationale**:
- `search()` returns **void** and pushes into signals — the container reads `products()`, `loading()`,
  `error()` reactively (mirrors how AuthService's `login()` mutates signals via `tap`). The container
  never handles the products array imperatively.
- `getById()` returns an **`Observable<ProductResponse>`** — a one-shot, parametric-by-route call with
  its own per-component success/404 handling; signal state in the singleton service would leak across
  navigations. The detail component subscribes locally.
- `listCategories()` returns an **`Observable<CategoryResponse[]>`** that is **cold-loaded once and
  cached** (see below). The container subscribes on init into a local `categories` signal.

### HttpParams construction (the strict-TDD core — explore risk #2)

Build params with EXACT lowercase keys and OMIT empty filter values so we never send
`name=` / `priceMin=` blanks that could confuse the Specification layer:

```ts
private buildSearchParams(filter: CatalogFilter, page: number): HttpParams {
  let params = new HttpParams()
    .set('page', String(page))                 // zero-based
    .set('size', String(this.DEFAULT_PAGE_SIZE));
  if (filter.name?.trim())          params = params.set('name', filter.name.trim());
  if (filter.categoryId != null)    params = params.set('categoryId', String(filter.categoryId));
  if (filter.priceMin != null)      params = params.set('priceMin', String(filter.priceMin));
  if (filter.priceMax != null)      params = params.set('priceMax', String(filter.priceMax));
  return params;
}
```

`HttpClient.get<PageResponse<ProductResponse>>(`${environment.apiBaseUrl}/api/products`, { params })`. The lowercase `page`/`size`
keys are asserted directly in the spec (see Test Plan) to lock the casing contract. `sort` is NOT sent
in this change (backend default ordering is acceptable for academic scope; add later if needed).

### Category cold-load strategy: **`shareReplay(1)` on a private Observable**

```ts
private categories$?: Observable<CategoryResponse[]>;

listCategories(): Observable<CategoryResponse[]> {
  if (!this.categories$) {
    this.categories$ = this.http
      .get<CategoryResponse[]>(`${environment.apiBaseUrl}/api/categories`)
      .pipe(shareReplay(1));     // multicast + cache the single response
  }
  return this.categories$;
}
```

Rationale (explore risk #4): `shareReplay(1)` fetches once and replays the cached list to every later
subscriber, so re-renders / repeated filter opens never refetch. Chosen over a signal cache because
categories are read once at container init into a local `categories` signal — the Observable cache is
the smallest correct primitive and is trivial to assert with `HttpTestingController` (`expectOne` once,
second call `expectNone`).

## State Model — where each signal lives, how refetch is triggered

| State | Lives in | Type | Notes |
|-------|----------|------|-------|
| products list | `ProductService` | `signal<ProductResponse[]>` | populated by `search()` |
| totalPages / totalElements | `ProductService` | `signal<number>` | drives pagination buttons |
| loading | `ProductService` | `signal<boolean>` | set true before GET, false on next/error |
| error | `ProductService` | `signal<string \| null>` | set on search failure |
| current filter | `CatalogComponent` | `signal<CatalogFilter>` | seeded `{}` |
| current page | `CatalogComponent` | `signal<number>` | zero-based, seeded `0` |
| categories | `CatalogComponent` | `signal<CategoryResponse[]>` | from `listCategories()` on init |

**Refetch flow** (single funnel — container method `runSearch()`):
1. `ngOnInit`: subscribe `listCategories()` → set `categories` signal; call `runSearch()` (page 0, empty filter).
2. `onFilterChange(filter)` (from `<app-catalog-filter>`, already debounced): set filter signal, **reset
   page signal to 0** (new filter ⇒ back to first page), call `runSearch()`.
3. `onPageChange(n)` (pagination button): set page signal to `n`, call `runSearch()` (filter unchanged).
4. `runSearch()` reads `filter()` + `page()` and calls `productService.search(filter, page)`.

**Debounce**: lives INSIDE `CatalogFilterComponent` on the name input only (`debounceTime(300)` +
`distinctUntilChanged()` on a `FormControl.valueChanges` or a subject), per explore risk #3. Category
and price changes emit immediately. The container never sees per-keystroke events. This keeps the
service free of timing concerns — it is purely a synchronous request builder (clean to strict-test).

## Routing — adding `/catalogo/:id`

Add a sibling lazy route in `app.routes.ts` immediately after the existing `catalogo` route. Keep the
`CatalogComponent` export name untouched (explore risk #6):

```ts
{
  path: 'catalogo/:id',
  loadComponent: () =>
    import('./features/catalog/product-detail.component').then((m) => m.ProductDetailComponent),
},
```

`ProductDetailComponent` reads the param via `ActivatedRoute`:

```ts
private readonly route = inject(ActivatedRoute);
private readonly productService = inject(ProductService);
// in ngOnInit: const id = Number(this.route.snapshot.paramMap.get('id'));
// this.productService.getById(id).subscribe({ next: ..., error: handle404 });
```

`snapshot.paramMap` is sufficient — detail is reached by a fresh navigation each time (no in-place id
swap), so a `paramMap` subscription is unnecessary complexity for this scope. Validate `Number(id)` is
finite before calling; a non-numeric id short-circuits to the not-found view without an HTTP call.

## Error / Empty / Loading states

| State | Trigger | UI | NotificationService |
|-------|---------|----|---------------------|
| Loading (list) | `loading()` true during `search()` | spinner / "Cargando..." in grid area | — |
| Empty (list) | `loading()` false AND `products().length === 0` AND no error | "No se encontraron productos" message | — |
| Error (list) | `search()` GET fails (5xx) | inline error block + retry affordance | `notify('No se pudieron cargar los productos', 'error')` |
| Loading (detail) | before `getById` resolves | "Cargando..." | — |
| 404 (detail) | `getById` returns 404 (inactive/missing product) | "Producto no encontrado" + link back to `/catalogo` | `notify('Producto no encontrado', 'error')` |
| Other error (detail) | non-404 failure | generic error view | `notify('No se pudo cargar el producto', 'error')` |

`errorInterceptor` already handles 401/403 globally; the catalog layer handles 404 (detail) and 5xx
(list) for user-facing messaging. `search()` uses `catchError` to set `_error` + flip `_loading` false
and (optionally) call `NotificationService.notify`; it re-throws nothing to the container (signal-driven).
For detail, the component's `subscribe({ error })` branches on `err.status === 404` vs other.

## `imageUrl: null` fallback — single source of truth

Export ONE placeholder constant and reference it at EVERY render site (card + detail), per explore
risk #1 (High). Add to `product.model.ts` next to `CatalogFilter`:

```ts
/** Fallback image when ProductResponse.imageUrl is null. Single source of truth. */
export const PLACEHOLDER_IMAGE = 'assets/placeholder-product.svg';
```

Render with the nullish-coalescing operator so a broken `<img src="null">` is impossible:

```html
<img [src]="product.imageUrl ?? PLACEHOLDER_IMAGE" [alt]="product.name" />
```

A static `assets/placeholder-product.svg` ships with the change (a simple inline SVG). Using `??`
(not `@if`) keeps a single `<img>` element and one code path. The constant — not a magic string — is
the single source of truth referenced by both card and detail.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `frontend/src/app/features/catalog/catalog.component.ts` | Modify (in-place) | Stub → smart container; signals orchestration; KEEP `CatalogComponent` export |
| `frontend/src/app/features/catalog/product.service.ts` | Create | HTTP client; signals; `buildSearchParams`; `shareReplay(1)` categories |
| `frontend/src/app/features/catalog/product.service.spec.ts` | Create | Strict RED-GREEN — HTTP + params + category cache |
| `frontend/src/app/features/catalog/product-card.component.ts` | Create | Presentational; `@Input() product`; routerLink; placeholder img |
| `frontend/src/app/features/catalog/product-card.component.spec.ts` | Create | Pragmatic render test |
| `frontend/src/app/features/catalog/catalog-filter.component.ts` | Create | Presentational; `@Output() filterChange`; internal name debounce |
| `frontend/src/app/features/catalog/catalog-filter.component.spec.ts` | Create | Pragmatic render test (debounce emit) |
| `frontend/src/app/features/catalog/product-detail.component.ts` | Create | Routed at `/catalogo/:id`; reads param; 404 handling |
| `frontend/src/app/features/catalog/product-detail.component.spec.ts` | Create | Pragmatic render test (loaded / 404 branches) |
| `frontend/src/app/app.routes.ts` | Modify | Add `catalogo/:id` lazy route |
| `frontend/src/app/models/product.model.ts` | Modify | Add `CatalogFilter` interface + `PLACEHOLDER_IMAGE` constant |
| `frontend/src/assets/placeholder-product.svg` | Create | Static fallback image |

## Test Plan

### Strict RED-GREEN (write the failing spec FIRST) — `product.service.spec.ts`

Pure logic over `HttpTestingController` (mirrors `auth.service.spec.ts`):

- `search()` GETs `/api/products` with method GET.
- **Params casing/content** — assert `req.request.params.get('page')` / `'size'` are present and `'size'`
  equals `'12'`; assert exact lowercase keys (locks explore risk #2).
- **Filter omission** — empty filter ⇒ params have NO `name`/`categoryId`/`priceMin`/`priceMax` keys
  (`req.request.params.has('name') === false`).
- **Filter composition** — a full filter ⇒ all four keys present with correct string values; whitespace-only
  `name` is omitted (`.trim()` guard).
- **Signal population** — on `flush(pageResponse)`, `products()`, `totalPages()`, `totalElements()` reflect
  the body; `loading()` is `true` mid-flight and `false` after.
- **Error path** — 5xx flush ⇒ `loading()` false, `error()` set, `products()` unchanged/empty.
- `getById(id)` GETs `/api/products/{id}`; success emits the product; 404 flush surfaces the error to the
  subscriber.
- **Category cache** — first `listCategories()` triggers exactly one GET `/api/categories`; a second
  subscription triggers NONE (`http.expectNone` / single `expectOne`), proving `shareReplay(1)`.

### Pragmatic render tests (critical branches only)

- `product-card.component.spec.ts` — renders name/price from `@Input() product`; `imageUrl: null` ⇒
  `<img>` src resolves to `PLACEHOLDER_IMAGE` (NOT `null`); routerLink targets `/catalogo/{id}`.
- `catalog-filter.component.spec.ts` — emits `filterChange` with the composed `CatalogFilter`; name
  input is debounced (use `fakeAsync` + `tick(300)` to assert a single emission after rapid keystrokes).
- `product-detail.component.spec.ts` — loaded branch renders the product; 404 branch renders
  "Producto no encontrado" and calls `NotificationService.notify` (mock the service + `ActivatedRoute`).
- `catalog.component.ts` (container) — OPTIONAL light render test: on init it calls `search()` once and
  renders the grid from a mocked `ProductService` signal; not exhaustive.

Components get NO strict RED-GREEN — DOM + change detection friction outweighs value at this scope
(consistent with the foundation's stance for Navbar). The service is the strict-TDD surface.

## Migration / Rollout

No migration, frontend-only, no backend touch (explore confirmed all three endpoints `permitAll()`).
Rollback = revert the new `features/catalog/*` files, the `product.model.ts` additions, the
`placeholder-product.svg`, and the `catalogo/:id` route line; the original `CatalogComponent` stub
returns from git history. Nothing else imports the new files.

## Open Questions

- [ ] None blocking. `sort` param and a user-adjustable page-size selector are explicitly deferred to a
      catalog v2; documenting here so TASKS does not accidentally pull them in.
