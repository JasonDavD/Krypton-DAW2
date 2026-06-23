# Proposal: Frontend Catalog — Real Product Catalog UI

> SDD PROPOSE phase. Planning-first: no code.
> Artifact store: hybrid (engram `sdd/frontend-catalog/proposal` + this file).
> Source: explore #362 / `explore.md`. Date: 2026-06-16.

## Intent

**Problem**: `/catalog` is a 19-line "Próximamente" stub. The `frontend-foundation`
skeleton (models, interceptors, signals, guards) is in place, but no real feature view
consumes the API yet. The backend exposes ready, PUBLIC catalog endpoints that nothing
on the frontend uses.

**Why now**: This is the first real feature on top of the foundation and the natural
next slice — all contracts are verified and **no backend work is required** (`GET /api/products`,
`GET /api/products/{id}`, `GET /api/categories` are all `permitAll()`).

**Success looks like**: A browsable product catalog — paginated grid with name/category/price
filters, a detail view at `/catalog/:id`, and explicit loading/empty/error states — built
entirely on existing foundation patterns and reusing `NotificationService` for errors.

## Scope

### In Scope
- Product list/grid with **server-side pagination** (Spring `page`/`size`/`totalPages`).
- Filters: **name** (debounced), **category** (dropdown from `GET /api/categories`), **price** (`priceMin`/`priceMax`).
- Product detail view at **`/catalog/:id`** consuming `GET /api/products/{id}`.
- Loading / empty / error states, reusing the existing `NotificationService` (404/5xx).
- `ProductService` (feature-scoped) with signal state, plus a thin co-located category fetch.

### Out of Scope
- Cart / add-to-cart actions → `frontend-cart-orders`.
- Auth-gated behavior (catalog is a public route; no token logic).
- Admin CRUD (products/categories) → `frontend-admin-core`.
- Infinite scroll (deferred to a v2 enhancement).
- Image upload / management.

## Capabilities

### New Capabilities
- `product-catalog`: public product list with server-side pagination + name/category/price filtering, and a product detail view at `/catalog/:id`, with loading/empty/error states.

### Modified Capabilities
- None. Replaces the `CatalogComponent` stub in-place and adds one lazy route — no existing spec-level behavior changes; the backend is untouched.

## Approach

**Decision 1 — ProductService location: `features/catalog/product.service.ts` (feature-scoped).**
Screaming architecture: `core/` stays infrastructure-only (auth, guards, interceptors,
notifications). The product client is domain-specific with a single consumer today. Promote
to `core/` only when a second consumer (cart/admin) actually appears — no premature generalization.

**Decision 2 — Pagination: page-button pagination (not infinite scroll).**
`PageResponse.totalPages` maps directly to Spring's `page`/`size` params, so button rendering
is trivial and the page state is a single `signal<number>`. Deep-linkable and easy to unit-test.
Infinite scroll adds scroll-detection and back-nav state-loss complexity for no academic-scope
gain — deferred to v2.

**Reuse of foundation patterns**: `signal<T>()` private + `.asReadonly()` public + `computed()`
for derived state (mirrors `AuthService`); `inject()` in components; `HttpClient` with the
already-wired `authInterceptor` + `errorInterceptor` (no token needed for public endpoints);
bare CSS in component `styles`; Jest strict RED-GREEN on `ProductService` HTTP logic, pragmatic
render tests on the card/detail components.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `frontend/src/app/features/catalog/catalog.component.ts` | Modified | Stub → smart container (keep `CatalogComponent` export name) |
| `frontend/src/app/features/catalog/product.service.ts` | New | HTTP client for products + categories + signal state |
| `frontend/src/app/features/catalog/product.service.spec.ts` | New | Strict TDD (pure HTTP logic) |
| `frontend/src/app/features/catalog/product-card.component.ts` | New | Presentational grid card |
| `frontend/src/app/features/catalog/product-detail.component.ts` | New | Detail view routed at `/catalog/:id` |
| `frontend/src/app/features/catalog/catalog-filter.component.ts` | New (optional) | Filter bar sub-component (DESIGN to confirm) |
| `frontend/src/app/app.routes.ts` | Modified | Add `/catalog/:id` lazy route |
| `frontend/src/app/models/product.model.ts` | Read-only | Types exist; optionally add `CatalogFilter` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `imageUrl` is `string \| null` — broken `<img src="null">` | High | Guard with `@if` / fallback `src` at every render site |
| Spring `Pageable` param casing — silent fallback to defaults | Med | Send exact lowercase `page`/`size`/`sort` via `HttpParams`; assert in spec |
| Name filter fires GET per keystroke | Med | Apply `debounceTime(300)` on the name input |
| Categories refetched per filter change | Med | Fetch once on catalog init (`take(1)`/`shareReplay(1)`) into a signal |
| `/catalog/:id` unreachable if route omitted | Med | Add the lazy route as an explicit task |
| `CatalogComponent` export renamed → lazy route breaks | Low | Replace stub in-place; do NOT rename the export |

## Rollback Plan

Low-risk by construction — frontend-only, no backend touch.
- Revert the new `features/catalog/` files and the `/catalog/:id` route line, restoring the
  original `CatalogComponent` stub (in git history). Nothing else imports the new files.
- No schema, data, or migration involved.

## Dependencies

- `frontend-foundation` (merged): models, interceptors, `NotificationService`, signal pattern.
- Backend running on `:8080` with the public catalog endpoints (already verified).

## Open Questions (for DESIGN — deferred, do not decide here)

1. **Default page size** — exact `size` default (e.g. 12 vs 20) and whether it is user-adjustable.
2. **`catalog-filter` as its own sub-component** vs inline filter markup in `CatalogComponent`.
3. **`product-card` presentational boundary** — `@Input()`-only card vs card owning its own navigation.
4. Whether to add a `CatalogFilter` interface to `product.model.ts` for the filter signal shape.

## Success Criteria

- [ ] `/catalog` renders a paginated product grid from `GET /api/products`.
- [ ] Name (debounced), category, and price filters drive server-side queries.
- [ ] `/catalog/:id` renders product detail from `GET /api/products/{id}`; 404 surfaces via `NotificationService`.
- [ ] Loading, empty, and error states are visible and correct.
- [ ] `imageUrl: null` renders a placeholder, never a broken image.
- [ ] `ProductService` HTTP logic covered by strict RED-GREEN Jest specs.
