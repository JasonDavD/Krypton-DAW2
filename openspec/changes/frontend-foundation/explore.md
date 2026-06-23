# Exploration: frontend-foundation — Krypton Angular SPA

> SDD EXPLORE phase. Planning-first: no code, no `ng new`. Investigation only.
> Artifact store: hybrid (engram `sdd/frontend-foundation/explore` #347 + this file).
> Date: 2026-06-15.

## Intent

Build the e-commerce frontend as an **Angular SPA** (standalone components) that
consumes the existing, mature Spring Boot REST API. The foundation is the
**contract**: translate backend DTOs into TypeScript interfaces *before* painting
any screen. This first change (`frontend-foundation`) delivers the skeleton that
unblocks every downstream feature change — it has no end-user screens of its own.

## Current State

- `frontend/` contains only a placeholder `README.md` — **greenfield**, nothing scaffolded.
- Backend is mature and 100% green (321 tests). Base package `pe.com.krypton`,
  layered architecture, DTOs at the boundary (no `@Entity` exposed).
- **Verified gaps in the backend that block the frontend:**
  - **No CORS configuration exists.** `config/` only holds `SecurityConfig.java`;
    a search for any CORS setup across `backend/src/main/java` returns zero matches.
    The browser will block `http://localhost:4200` → `http://localhost:8080`.
    → A `CorsConfig.java` (dev origin allowlist) MUST be added as a backend task in this change.

## The Contract (what the frontend must mirror)

Security rules (from `SecurityConfig.java`):

- **Public**: `/api/auth/**`, `GET /api/products/**`, `GET /api/categories/**`
- **JWT (any role)**: everything else (`anyRequest().authenticated()`)
- **JWT + ADMIN**: `/api/admin/**`
- Token: `Authorization: Bearer <token>`, HS256, 24h expiry.
- **JWT `role` claim is the raw enum value** (`"ADMIN"` / `"CLIENTE"`), NOT `"ROLE_ADMIN"`.
  Verified: `JwtService.java:36` emits `user.getRole().name()`; the `ROLE_` prefix is
  added only server-side in `CustomUserDetailsService.java:32` for `hasRole()`.
  → Angular guards compare against `'ADMIN'`.

Endpoint groups (≈36 endpoints across 10 controllers — exact list to be locked in the SPEC):

| Group | Auth |
|-------|------|
| Auth (login, register) | Public |
| Catalog (products, categories — read) | Public |
| Cart (5) | JWT |
| Orders — client (4) | JWT |
| Admin products / categories / orders / users | JWT + ADMIN |
| Admin reports (Excel/PDF binary blobs) | JWT + ADMIN |

TypeScript interfaces to create (mirror of the DTOs):

- **Responses**: `AuthResponse`, `UserResponse`, `CategoryResponse`, `ProductResponse`,
  `PageResponse<T>`, `CartItemResponse`, `CartResponse`, `OrderItemResponse`,
  `OrderResponse`, `ApiError`.
- **Enums (union types)**: `Role = 'CLIENTE' | 'ADMIN'`,
  `OrderStatus = 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA'`,
  `PaymentMethod = 'CREDIT_CARD' | 'YAPE' | 'EFECTIVO'`.
- **Requests**: `LoginRequest`, `RegisterRequest`, `CartItemRequest`,
  `UpdateQuantityRequest`, `PaymentRequest`, `ProductRequest`, `CategoryRequest`,
  `CreateUserRequest`, `UpdateRoleRequest`, `UpdateStatusRequest`, `OrderStatusUpdateRequest`.

Type-mapping notes (to confirm in DESIGN):

- `BigDecimal` → `number` (document precision as a known academic-scope limitation).
- `Instant` → ISO 8601 `string` (default with Spring Boot's `JavaTimeModule`; confirm with a real request).
- Error envelope: `{ status: number; error: string }` (validation errors joined into the single `error` string).
- Report endpoints return **binary blobs** → Angular `HttpClient` with `{ responseType: 'blob' }`.

## Architecture (recommended, with rejected alternatives)

**State management → Angular Signals** (`signal()` + `computed()`).
Rejected: plain Services + RxJS `BehaviorSubject` (subscription footguns); NgRx
(disproportionate boilerplate for an academic 2–3 dev scope). Signals is the modern
Angular 17+ idiom and reads cleanly in the EFSRT documentation.

**Folder structure → feature-based.** Single `src/app/models/` directory, one file
per domain (`auth.model.ts`, `product.model.ts`, `cart.model.ts`, `order.model.ts`,
`api.model.ts`). Rejected per-feature models (`OrderResponse` is shared by client and
admin features → import coupling).

## Open decisions (defer to DESIGN — do NOT decide here)

- **Test runner**: Karma/Jasmine (CLI default, deprecated in Angular 18+) vs Jest
  (fast, headless) vs Vitest (fastest, Angular support still maturing in 2026).
- **Angular version**: pin ≥18 to avoid Karma deprecation debt.
- **Strict-TDD realism**: strict for pure logic (services, guards, mockable HttpClient);
  pragmatic render-tests for components only on critical paths.

## Recommended slicing (5 changes, dependency-ordered)

1. **`frontend-foundation`** ← scoped here
2. `frontend-catalog` — product list/search + detail (public)
3. `frontend-cart-orders` — cart + checkout + order history (CLIENTE)
4. `frontend-admin-core` — admin CRUD: products, categories, orders, users
5. `frontend-admin-reports` — report download screens (date pickers, blob download)

### `frontend-foundation` scope

Everything with zero end-user business value but blocking all downstream changes:

1. `ng new` Angular ≥17 scaffold (standalone, SCSS).
2. All TS model interfaces (`src/app/models/`).
3. `environment.ts` with `apiBaseUrl`.
4. `HttpClient` providers + `AuthInterceptor` (attach Bearer) + `ErrorInterceptor` (401/403).
5. `AuthService` (`login`/`register`/`logout`) with `currentUser` + `role` signals.
6. Functional `AuthGuard` + `AdminGuard`.
7. App router scaffold (lazy-loaded stubs for not-yet-built features).
8. `NavbarComponent` (auth-reactive) + `FooterComponent`.
9. **Backend task**: `CorsConfig.java` allowing `http://localhost:4200` (dev).

## Risks

1. **CRITICAL — no CORS config** (verified). Angular dev server blocked immediately;
   fix as a backend task in this change.
2. JWT role claim is `"ADMIN"`, not `"ROLE_ADMIN"` (verified) — guard comparison must match.
3. `Instant` serialization format unconfirmed — verify with a real request before locking TS types.
4. Report blob download needs the non-obvious `{ responseType: 'blob' }` pattern — document in spec.
5. `BigDecimal` → JS `number` precision — document as known limitation.

## Verdict

Ready for PROPOSE. Contract enumerated, architecture recommended (Signals + feature-based),
first-change scope bounded, open decisions correctly surfaced for DESIGN.
