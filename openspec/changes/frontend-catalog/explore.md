# Exploration: frontend-catalog — Real Product Catalog UI

> SDD EXPLORE phase. Investigation only — no code created.
> Artifact store: hybrid (engram `sdd/frontend-catalog/explore` #362 + this file).
> Date: 2026-06-16.

## Intent

Replace the `/catalog` "Próximamente" stub with a real product catalog UI that consumes the two PUBLIC backend endpoints (`GET /api/products` and `GET /api/products/{id}`). First real feature view on top of `frontend-foundation`. Scope: product list/grid with server-side pagination and filtering, category filter dropdown, and a product detail view.

## Current State

### Stub under replacement
`frontend/src/app/features/catalog/catalog.component.ts` — 19-line standalone "Próximamente" component. Export name `CatalogComponent` MUST NOT be renamed (lazy route references it by name).

### Routes today
`/catalog` → `CatalogComponent` (public, no guard). No `/catalog/:id` exists — must be added.

### Foundation patterns (must follow)
- Signals: `signal<T>()` private, `.asReadonly()` public, `computed()` for derived state.
- Services: `@Injectable({ providedIn: 'root' })`, `inject()` in components.
- HTTP: `HttpClient` with `authInterceptor` + `errorInterceptor` already wired. No token required for public endpoints.
- NotificationService: `notify(message, type?)` for user-facing errors.
- No CSS framework — bare CSS in component `styles: [...]`.
- Jest 29 + `jest-preset-angular@14.x`, command `cd frontend && npm test`.

### Models already exist — do NOT recreate
`frontend/src/app/models/product.model.ts`: `ProductResponse`, `CategoryResponse`, `PageResponse<T>` — verified against Java records.

### Backend contracts verified

**Products (public)**
- `GET /api/products` → `PageResponse<ProductResponse>`. Optional params: `name`, `categoryId`, `priceMin`, `priceMax` + Spring Pageable (`page`, `size`, `sort`). Always filters `active = true` at the Specification layer.
- `GET /api/products/{id}` → `ProductResponse`. Returns 404 for inactive products.
- `SecurityConfig.java`: `requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()`

**Categories (public — CONFIRMED)**
- `GET /api/categories` → `List<CategoryResponse>`. Public, same `permitAll()` rule.
- Write ops at `/api/admin/categories` (separate `AdminCategoryController`) — unrelated.
- **Category filter dropdown is feasible. No backend changes needed.**

**Active filter — backend-enforced**
`ProductServiceImpl.search()` always applies `ProductSpecification.isActive(true)`. Frontend does NOT need to filter `active` client-side.

## Affected Areas

| File | Status | Why |
|------|--------|-----|
| `frontend/src/app/features/catalog/catalog.component.ts` | Replace in-place | Stub → real container component |
| `frontend/src/app/app.routes.ts` | Modify | Add `/catalog/:id` route |
| `frontend/src/app/features/catalog/product.service.ts` | Create | HTTP client for products + categories |
| `frontend/src/app/features/catalog/product.service.spec.ts` | Create | Strict TDD (pure HTTP logic) |
| `frontend/src/app/features/catalog/product-card.component.ts` | Create | Presentational card |
| `frontend/src/app/features/catalog/product-detail.component.ts` | Create | Detail view routed at `/catalog/:id` |
| `frontend/src/app/features/catalog/catalog-filter.component.ts` | Create (optional) | Filter bar sub-component |
| `frontend/src/app/models/product.model.ts` | Read-only | Types exist; optionally add `CatalogFilter` |

## Approaches

### 1. ProductService location

| Approach | Pros | Cons | Effort |
|----------|------|------|--------|
| A — `features/catalog/product.service.ts` | Screaming arch; `core/` stays infra-only; co-located with consumer | Cross-feature import if admin/cart need it later | Low |
| B — `core/products/product.service.ts` | No future move; mirrors AuthService | Premature generalization; no second consumer today | Low |

### 2. Pagination UX

| Approach | Pros | Cons | Effort |
|----------|------|------|--------|
| A — Page buttons | Trivial signal state; maps directly to Spring `page` param; testable | Less fluid UX | Low |
| B — Infinite scroll | Modern catalog feel | Scroll detection; back-nav state loss; no Angular built-in | Medium–High |

## Recommendation

- **Service location**: Start in `features/catalog/`. Promote to `core/` when a second consumer appears.
- **Pagination**: Page buttons. `PageResponse.totalPages` makes rendering trivial. Infinite scroll is a v2 enhancement.

## Risks

1. `imageUrl` is `string | null` — every render site needs a fallback `src` or `@if` guard; silent visual bug if missed.
2. Spring `Pageable` param names must be exact lowercase (`page`, `size`, `sort`) in `HttpParams` — mismatch silently falls back to defaults.
3. Name filter needs `debounceTime(300)` — every keystroke without it fires a GET request.
4. Categories must be fetched once on catalog init — use `take(1)` into a signal or `shareReplay(1)`.
5. `/catalog/:id` route must be explicitly added — no Angular error if absent, just an unreachable URL.
6. `CatalogComponent` export name constraint — replace stub in-place, do NOT rename the export.

## Verdict

Ready for PROPOSE. All backend contracts verified. No backend work needed. Two design forks surfaced (service location, pagination). Scope bounded to ~6 files + 1 route update. Foundation patterns fully understood and must be followed.
