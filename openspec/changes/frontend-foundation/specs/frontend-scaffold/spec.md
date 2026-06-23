# Frontend Scaffold Specification

## Purpose

Defines behavior for the Angular SPA skeleton: application bootstrap, DTO model interfaces,
environment configuration, layout shell, and the top-level route structure. This is the
shared foundation every downstream feature change plugs into.

## Requirements

### Requirement: Application Bootstrap

The application MUST compile and start without errors, rendering a layout shell that
contains a navbar and a footer on every route.

#### Scenario: First load

- GIVEN a user opens `http://localhost:4200`
- WHEN the Angular application bootstraps
- THEN a page with a visible navbar and a visible footer is rendered
- AND no console errors appear during startup

#### Scenario: Navigation does not destroy shell

- GIVEN the application is running and the shell is visible
- WHEN the user navigates to any declared route
- THEN the navbar and footer remain visible
- AND only the router outlet content changes

---

### Requirement: DTO Model Interfaces

The system MUST expose TypeScript interfaces that mirror every backend DTO used by the
API, grouped by domain, under `src/app/models/`.

The system SHALL provide at minimum interfaces for: auth (login request/response,
register request), user, product, category, cart, cart-item, order, order-item,
and report output shapes.

#### Scenario: Type contract for auth flow

- GIVEN a developer writes a login call
- WHEN they type the request payload
- THEN TypeScript resolves the shape from `src/app/models/auth.model.ts` (or equivalent)
- AND the compiler rejects any missing or mistyped field

#### Scenario: Type contract for numeric precision

- GIVEN a product interface declares a price field
- WHEN the interface is defined
- THEN the price field MUST be typed as `number` with a documented comment noting
  potential `BigDecimal` precision loss for academic-scope awareness

---

### Requirement: Environment Configuration

The system MUST declare `environment.apiBaseUrl` pointing to `http://localhost:8080`
for the development environment.

#### Scenario: Base URL available at runtime

- GIVEN the Angular app is running in development mode
- WHEN any service constructs an API URL
- THEN it reads `environment.apiBaseUrl` and the resulting URL starts with
  `http://localhost:8080`

#### Scenario: Missing base URL is a compile error

- GIVEN `environment.apiBaseUrl` is removed or renamed
- WHEN the application is compiled
- THEN the TypeScript compiler produces an error referencing the missing property

---

### Requirement: Lazy Stub Route Declarations

The router MUST declare stub routes for catalog, cart, orders, admin, and reports as
lazy-loaded entries so downstream changes can fill each slot without modifying the
skeleton.

#### Scenario: Stub route resolves without 404

- GIVEN the application is running
- WHEN a user navigates to `/catalog`, `/cart`, `/orders`, `/admin`, or `/reports`
- THEN the router resolves the route (no `Cannot match routes` error in console)
- AND an empty or placeholder view is rendered (stub component is acceptable)

#### Scenario: Adding a downstream feature does not require modifying the scaffold

- GIVEN a downstream change owns one stub route slot
- WHEN the downstream change replaces the stub `loadComponent` target
- THEN no other route declaration needs to change

---

### Requirement: Auth-Reactive Navbar

The `NavbarComponent` MUST reflect the current authentication state: show login/register
links when unauthenticated, show the username and a logout action when authenticated.

#### Scenario: Unauthenticated state

- GIVEN no token is stored and `AuthService.currentUser` signal is null
- WHEN the navbar renders
- THEN login and register navigation links are visible
- AND no logout control is visible

#### Scenario: Authenticated state

- GIVEN a valid token is stored and `AuthService.currentUser` signal is set
- WHEN the navbar renders
- THEN the current user's identifier is displayed
- AND a logout control is visible
- AND login/register links are NOT visible
