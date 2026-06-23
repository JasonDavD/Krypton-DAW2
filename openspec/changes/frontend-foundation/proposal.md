# Proposal: Frontend Foundation — Angular SPA Skeleton

> SDD PROPOSE phase. Planning-first: no code, no `ng new`.
> Artifact store: hybrid (engram `sdd/frontend-foundation/proposal` + this file).
> Source: explore #347. Date: 2026-06-15.

## Intent

**Problem**: The Krypton e-commerce backend is mature (321 tests green), but `frontend/`
is greenfield — only a placeholder README. Nothing can consume the API yet, and the
backend has **no CORS configuration**, so a browser on `:4200` is blocked from `:8080`.

**Why now**: This is the first move of the frontend phase and the EFSRT V evaluated
deliverable. Every downstream feature change (catalog, cart, admin, reports) depends on
a shared contract — TS DTO mirrors, HTTP layer, auth state, routing skeleton — that does
not exist. Building screens before this foundation would duplicate plumbing and produce
ungradeable, incoherent code.

**Success looks like**: A compiling Angular SPA that boots to a layout shell (navbar +
footer), with the full type contract, interceptor-backed HttpClient, working auth state,
and route guards in place — so two developers can start independent feature changes in
parallel. No domain screen is rendered yet by design.

## Scope

### In Scope
- `ng new` standalone Angular scaffold (SCSS, router in `main.ts`) inside `frontend/`.
- All TS model interfaces mirroring the DTOs → single `src/app/models/` (one file per domain).
- `environment.ts` with `apiBaseUrl` (`http://localhost:8080`).
- `HttpClient` providers + functional `AuthInterceptor` (Bearer) + `ErrorInterceptor` (401/403).
- `AuthService` (`login` / `register` / `logout`) exposing `currentUser` + `role` signals.
- Functional `AuthGuard` + `AdminGuard` (guard compares role against `'ADMIN'`).
- App router scaffold: all routes declared as lazy stub routes for not-yet-built features.
- `NavbarComponent` (auth-reactive) + `FooterComponent`.
- **Backend task**: `CorsConfig.java` in `pe.com.krypton.config` allowing `http://localhost:4200` (dev).

### Out of Scope (the 4 downstream changes)
- Catalog UI (product list, search/filter, detail) → `frontend-catalog`.
- Cart UI, checkout flow, order history → `frontend-cart-orders`.
- Admin CRUD (products, categories, orders, users) → `frontend-admin-core`.
- Admin report download screens → `frontend-admin-reports`.
- Any form calling the real API beyond `AuthService`.

## Capabilities

### New Capabilities
- `frontend-scaffold`: Angular standalone app, models, environment, layout shell.
- `frontend-http-auth`: HttpClient providers, interceptors, AuthService, route guards.
- `cors-config`: backend dev-origin allowlist for the Angular dev server.

### Modified Capabilities
- None (greenfield frontend; the backend change is additive config, no existing behavior altered).

## Approach

Signals + feature-based architecture (locked in explore). Contract-first: type the DTOs
before any screen. The HTTP layer is two functional interceptors — `AuthInterceptor`
attaches the Bearer token, `ErrorInterceptor` reacts to 401/403. `AuthService` is the
single source of auth truth via signals; guards read it. Routes are declared up front as
lazy stubs so each downstream change fills one slot without touching the skeleton.

The **CORS task** is the only backend touch and the only cross-cutting risk: without it
the SPA cannot reach the API at all. It is one isolated `@Configuration` class scoped to
the dev origin — small surface, high importance. The role-claim fact (raw `'ADMIN'`, not
`'ROLE_ADMIN'`) is the other contract detail guards must honor.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `frontend/` (whole) | New | Angular scaffold, app bootstrap, config |
| `frontend/src/app/models/` | New | DTO mirror interfaces |
| `frontend/src/app/core/` | New | interceptors, guards, `AuthService` |
| `frontend/src/app/` (shell) | New | `NavbarComponent`, `FooterComponent`, router scaffold |
| `frontend/src/environments/` | New | `apiBaseUrl` |
| `backend/.../pe/com/krypton/config/` | New | `CorsConfig.java` (dev allowlist) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| No CORS → SPA blocked from API | High (verified) | Ship `CorsConfig.java` as a task in THIS change |
| Guard checks wrong role string | Med | Compare against `'ADMIN'` (raw enum), not `'ROLE_ADMIN'` |
| `Instant` serialization format unconfirmed | Med | Verify with a real request before locking TS types (DESIGN) |
| `BigDecimal` → JS `number` precision loss | Low | Document as known academic-scope limitation |

## Rollback Plan

Low-risk by construction — greenfield frontend plus one isolated backend class.
- **Frontend**: delete the `frontend/src/app` additions (or `git revert` the change). Nothing
  else in the repo imports them; the backend is untouched by the SPA.
- **Backend**: remove `CorsConfig.java`. It is a standalone `@Configuration` with no
  dependencies on other config; deleting it restores the prior (no-CORS) state exactly.
  No schema, no data, no migration involved.

## Dependencies

- Backend running on `:8080`; Angular dev server on `:4200`.
- Open decisions below must be resolved in DESIGN before scaffolding.

## Open Questions (for DESIGN — deferred, do not decide here)

1. **Test runner**: Karma/Jasmine (CLI default, deprecated in Angular 18+) vs Jest vs Vitest.
2. **Angular version**: pin ≥18 to avoid Karma deprecation debt.
3. **Strict-TDD realism for components**: strict for services + guards (pure logic);
   pragmatic render-tests for components on critical paths only.

## Success Criteria

- [ ] Angular SPA compiles and boots to the layout shell (navbar + footer).
- [ ] All DTO interfaces exist under `src/app/models/`.
- [ ] `CorsConfig.java` lets `:4200` reach `:8080` (login round-trips in dev).
- [ ] `AuthService` login sets `currentUser` + `role` signals; logout clears them.
- [ ] `AuthGuard` / `AdminGuard` gate routes correctly (`AdminGuard` requires `'ADMIN'`).
- [ ] Lazy stub routes declared for catalog, cart, orders, admin, reports.
