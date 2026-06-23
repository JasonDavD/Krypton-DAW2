# Product Catalog Specification

> Delta spec for `frontend-catalog`. Phase: SDD SPEC.
> Artifact store: hybrid (engram `sdd/frontend-catalog/spec` + this file).
> Source: proposal `sdd/frontend-catalog/proposal`. Date: 2026-06-16.
>
> **Capability split decision**: one capability (`product-catalog`) covers both list and
> detail views. Rationale: both views are served by the same `ProductService`, share the
> same loading/error/empty states, and the split would add structural ceremony without
> adding clarity. DESIGN may introduce sub-component boundaries internally.

## Purpose

Defines what MUST be true after `frontend-catalog` is applied. Describes observable
behaviour and contracts — not implementation. The backend is unchanged; all three
endpoints are `permitAll()` (no auth logic in scope).

**TS types referenced throughout**: `ProductResponse`, `CategoryResponse`,
`PageResponse<T>` — all defined in `frontend/src/app/models/product.model.ts`.

---

## Capability: product-catalog

---

### Requirement: Paginated Product Grid

`/catalogo` MUST render a grid of products fetched from `GET /api/products`.
Results are server-side paginated using Spring Pageable params.
Navigation between pages uses explicit page buttons driven by `PageResponse.totalPages`.

> **NOTE — DESIGN decision pending**: the default `size` value (e.g., 12 vs 20) and
> whether size is user-adjustable are deferred to the DESIGN phase. The spec only
> constrains the mechanics and param names.

#### Scenario: Initial load renders first page

- GIVEN a user navigates to `/catalogo`
- WHEN `ProductService` sends `GET /api/products?page=0&size=<default>`
- THEN the response `PageResponse<ProductResponse>.content` is rendered as a grid of cards
- AND each card shows at minimum the product name and price
- AND `page` and `size` query params are lowercase (Spring Pageable contract)

#### Scenario: Page navigation sends correct params

- GIVEN the catalog has rendered page 0 of a multi-page result
- WHEN the user clicks a page button for page N (1-based display, 0-based param)
- THEN `ProductService` sends `GET /api/products?page=<N>&size=<default>` (plus any
  active filter params)
- AND the grid re-renders with the content of page N

#### Scenario: Page buttons reflect total pages

- GIVEN `PageResponse.totalPages` equals T
- WHEN the page-button row renders
- THEN exactly T page buttons are present
- AND the button corresponding to the current `PageResponse.page` is marked active

#### Scenario: Single-page result hides pagination controls

- GIVEN `PageResponse.totalPages` equals 1
- WHEN the catalog renders
- THEN no page-navigation buttons are rendered

---

### Requirement: Product Filtering

Name, category, and price-range filters MUST compose into query params on the same
`GET /api/products` request. Any combination of filters is valid.
Changing any filter resets pagination to page 0.

#### Scenario: Name filter debounced — no per-keystroke request

- GIVEN the catalog is rendered
- WHEN the user types "cami" into the name input, character by character
- THEN no HTTP request is sent until at least 300 ms have elapsed since the last keystroke
- AND after the debounce interval `GET /api/products?page=0&name=cami` is sent

#### Scenario: Category filter drives dropdown from API

- GIVEN the catalog initialises
- WHEN `GET /api/categories` returns a list of `CategoryResponse`
- THEN a dropdown is rendered containing one option per category (using `CategoryResponse.name`)
- AND categories are fetched exactly once per catalog mount (not re-fetched on filter change)

#### Scenario: Category selected adds param

- GIVEN the category dropdown is populated
- WHEN the user selects a category with `CategoryResponse.id` equal to 3
- THEN the next product request includes `categoryId=3` in the query params
- AND page resets to 0

#### Scenario: Price range filter sends priceMin and priceMax

- GIVEN the user enters 50 in the min-price input and 200 in the max-price input
- WHEN the filter is applied (exact trigger — submit button vs reactive — is a DESIGN decision)
- THEN the product request includes `priceMin=50&priceMax=200` in the query params
- AND page resets to 0

#### Scenario: Filters compose — name + category + price sent together

- GIVEN the user has entered name "cami", selected categoryId 3, and entered priceMin 50
- WHEN a product request is triggered
- THEN the request URL contains all three params: `name=cami`, `categoryId=3`, `priceMin=50`
- AND only params with non-empty values are appended (absent optional params are omitted)

#### Scenario: Clearing all filters restores unfiltered request

- GIVEN one or more filters are active
- WHEN the user clears all filter inputs and resets the category dropdown to "all"
- THEN the next request is `GET /api/products?page=0&size=<default>` with no filter params

---

### Requirement: Product Detail View

`/catalogo/:id` MUST render the full detail of one product fetched from
`GET /api/products/{id}`. The route MUST be a lazy-loaded entry in `app.routes.ts`
separate from the list route.

#### Scenario: Detail renders product fields

- GIVEN a user navigates to `/catalogo/7`
- WHEN `GET /api/products/7` returns a `ProductResponse`
- THEN the view displays at minimum: product name, price, description, category name, and stock
- AND `imageUrl` is rendered as an image element (see Req: imageUrl Null Fallback)

#### Scenario: 404 surfaces NotificationService — not a blank page

- GIVEN a user navigates to `/catalogo/9999`
- WHEN `GET /api/products/9999` returns HTTP 404
- THEN `NotificationService` is called with an error message indicating the product was not found
- AND the view does NOT crash or render a blank white page (a user-visible not-found
  message or redirect is acceptable — exact UX is a DESIGN decision)

#### Scenario: Back navigation returns to catalog list

- GIVEN a user is on `/catalogo/7`
- WHEN they activate a "back" or "return to catalog" control
- THEN the router navigates to `/catalogo` (the list view)

---

### Requirement: Loading State

Every async operation (product list fetch, detail fetch, category fetch) MUST render
a visible loading indicator while the HTTP request is in flight.

#### Scenario: List shows loading on initial fetch

- GIVEN `/catalogo` has just been navigated to
- WHEN the product request is in flight
- THEN a loading indicator is visible
- AND no product cards are rendered yet

#### Scenario: Loading indicator disappears after response

- GIVEN the loading indicator is visible
- WHEN `GET /api/products` returns any response (success or error)
- THEN the loading indicator is no longer rendered

#### Scenario: Detail shows loading before response

- GIVEN a user navigates to `/catalogo/:id`
- WHEN the detail request is in flight
- THEN a loading indicator is visible in the detail view area

---

### Requirement: Empty State

When `GET /api/products` returns `content: []` (no results), the catalog MUST render
a user-visible empty-state message — not a blank grid.

#### Scenario: Empty result set renders empty state

- GIVEN filters are applied (or the catalog has no products)
- WHEN `PageResponse.content` is an empty array
- THEN a user-visible message indicating no products were found is rendered
- AND no product cards are rendered
- AND no pagination controls are rendered

---

### Requirement: Error State — HTTP Failures

HTTP errors on product list or detail fetches MUST surface via `NotificationService`.
The component MUST NOT crash or remain silently empty.

#### Scenario: 5xx on product list surfaces notification

- GIVEN the catalog is loading
- WHEN `GET /api/products` returns HTTP 500
- THEN `NotificationService` is called with an error message
- AND the loading indicator is removed
- AND the grid renders an error state (not a blank screen)

#### Scenario: 5xx on detail view surfaces notification

- GIVEN the detail view is loading
- WHEN `GET /api/products/{id}` returns HTTP 500
- THEN `NotificationService` is called with an error message
- AND the loading indicator is removed

#### Scenario: Category fetch error does not crash the catalog

- GIVEN the catalog is initialising
- WHEN `GET /api/categories` fails with any HTTP error
- THEN `NotificationService` is called with an error message
- AND the catalog grid is still rendered (with an empty or disabled category dropdown)
- AND the rest of the filter functionality remains operable

---

### Requirement: imageUrl Null Fallback

`ProductResponse.imageUrl` is typed `string | null`. Every component that renders a
product image MUST handle the null case without producing a broken `<img>` tag.

#### Scenario: Non-null imageUrl renders the image

- GIVEN a `ProductResponse` with `imageUrl: "https://example.com/product.jpg"`
- WHEN a product card or detail view renders
- THEN an `<img>` element is rendered with `src` equal to the `imageUrl` value

#### Scenario: Null imageUrl renders a placeholder — not a broken image

- GIVEN a `ProductResponse` with `imageUrl: null`
- WHEN a product card or detail view renders
- THEN no `<img src="null">` or `<img src="">` element is present
- AND a visible placeholder (e.g., a fallback image, an icon, or a styled placeholder
  block) is rendered in its place

---

### Requirement: Inactive Product Exclusion

Inactive products (`ProductResponse.active === false`) MUST NOT appear in the catalog
grid or be reachable at `/catalogo/:id`.

> **NOTE — Backend-enforced assumption**: This requirement is satisfied by the backend
> (`GET /api/products` and `GET /api/products/{id}` filter by `active = true`).
> The frontend MUST NOT re-filter responses client-side and MUST NOT assume
> `active === true` is always present — but the frontend is not responsible for
> enforcing this invariant. If a product with `active: false` is returned by the API,
> that is a backend defect outside this change's scope.

#### Scenario: Assumption — API never returns inactive products (backend contract)

- GIVEN the backend is functioning correctly
- WHEN `GET /api/products` or `GET /api/products/{id}` returns a response
- THEN all items in the response have `active === true`
- AND the frontend renders them without inspecting the `active` field

---

### Requirement: Route Registration

`/catalogo/:id` MUST be registered as a lazy-loaded route in `app.routes.ts`.
The existing `/catalogo` route MUST continue to work, loading `CatalogComponent`
by the same export name (no rename).

#### Scenario: /catalogo route loads the list component

- GIVEN `app.routes.ts` is registered
- WHEN the router resolves `/catalogo`
- THEN `CatalogComponent` from `features/catalog/catalog.component.ts` is loaded
- AND no console error about an unmatched route appears

#### Scenario: /catalogo/:id route loads the detail component

- GIVEN `app.routes.ts` is registered
- WHEN the router resolves `/catalogo/42`
- THEN the lazy-loaded detail component for that ID is activated
- AND no console error about an unmatched route appears

#### Scenario: CatalogComponent export name unchanged

- GIVEN the detail route and the list route are both registered
- WHEN any file references `CatalogComponent`
- THEN the import resolves without error to `features/catalog/catalog.component.ts`
  (the export name MUST remain `CatalogComponent`)

---

### Requirement: ProductService Signal Contract

`ProductService` MUST expose its state as Angular signals following the pattern
established by `AuthService` in `frontend-http-auth`.

#### Scenario: Service exposes readonly signals

- GIVEN `ProductService` is injected into a component
- WHEN the component reads product and loading state
- THEN it accesses public readonly signals (not subjects, not plain properties)
- AND writing to those signals from outside the service is a TypeScript compile error

#### Scenario: HTTP call result updates signal — success path (strict RED-GREEN)

- GIVEN `ProductService` is under test with `HttpClientTestingModule`
- WHEN `loadProducts(page, size, filter)` is called and the mock returns a `PageResponse<ProductResponse>`
- THEN the `products` signal value equals `PageResponse.content`
- AND the `totalPages` signal value equals `PageResponse.totalPages`
- AND the `loading` signal is false after the response

#### Scenario: HTTP call result updates signal — error path (strict RED-GREEN)

- GIVEN `ProductService` is under test
- WHEN `loadProducts(...)` is called and the mock returns HTTP 500
- THEN the `loading` signal is false
- AND the `products` signal remains unchanged (does not become null/undefined)
- AND the error is delegated to `NotificationService`

#### Scenario: Category list fetched once and cached

- GIVEN `ProductService` is under test
- WHEN `loadCategories()` is called more than once during a catalog session
- THEN `GET /api/categories` is sent exactly once over the lifetime of that service instance
- AND subsequent reads of the `categories` signal return the cached value
