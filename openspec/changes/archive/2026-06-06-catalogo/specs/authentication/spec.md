# authentication Specification — Delta (catalogo change)

## Purpose

This file contains ONLY the requirements added or modified by the `catalogo` change.
The full baseline specification lives at `openspec/specs/authentication/spec.md`.
All baseline requirements remain in force unless explicitly overridden here.

---

## Modified Requirement: Borde de seguridad (MODIFIED)

> Baseline: `/api/auth/**` y la documentación (Swagger) MUST ser públicos. Todo otro
> endpoint MUST requerir autenticación.

**ADDED:** GET requests to `/api/products/**` and `/api/categories/**` MUST also be
publicly accessible (no JWT required). `SecurityConfig` MUST configure
`.requestMatchers(GET, "/api/products/**", "/api/categories/**").permitAll()` ordered
BEFORE the `anyRequest().authenticated()` catch-all.

ADMIN write endpoints (`/api/admin/**`) already require role ADMIN via the existing
`.requestMatchers("/api/admin/**").hasRole("ADMIN")` rule; this remains unchanged.

All non-auth endpoints not listed as public above MUST continue to require
authentication (`anyRequest().authenticated()`).

#### Scenario: Public catalog GET without token (ADDED)

- GIVEN no Authorization header is sent
- WHEN the client calls `GET /api/products` or `GET /api/products/{id}` or `GET /api/categories` or `GET /api/categories/{id}`
- THEN the request is accepted (responds 200 or 404 depending on data, never 401)

#### Scenario: ADMIN catalog write still requires ADMIN role (UNCHANGED — verified)

- GIVEN a valid JWT with role CLIENTE
- WHEN the client calls `POST /api/admin/products` or `POST /api/admin/categories` or any `PUT`/`DELETE` under `/api/admin/**`
- THEN responds 403

#### Scenario: Non-catalog protected endpoints still require authentication (UNCHANGED — verified)

- GIVEN no Authorization header
- WHEN a non-public endpoint such as `GET /api/admin/users` is called
- THEN responds 401

#### Scenario: POST to catalog path still requires authentication (BOUNDARY CLARIFICATION — ADDED)

- GIVEN no Authorization header
- WHEN `POST /api/products` is called (a path that begins with `/api/products/` but uses a non-GET method)
- THEN the request MUST NOT be authorized by the permitAll rule; the response MUST be 401 or 403 depending on role; the `permitAll` rule MUST be scoped to GET method only
