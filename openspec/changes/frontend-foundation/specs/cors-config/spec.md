# CORS Configuration Specification

## Purpose

Defines behavior for the backend CORS policy that allows the Angular development server
(`http://localhost:4200`) to make cross-origin requests to the Spring Boot API
(`http://localhost:8080`). Greenfield: no CORS configuration currently exists.

## Requirements

### Requirement: Dev Origin Allowlist

The backend MUST permit cross-origin requests originating from `http://localhost:4200`.
All other origins MUST remain subject to the browser's default same-origin policy
(no wildcard allowlist).

#### Scenario: Request from Angular dev server is accepted

- GIVEN the backend is running and the Angular dev server is on port 4200
- WHEN the browser sends a cross-origin HTTP request with `Origin: http://localhost:4200`
- THEN the response includes `Access-Control-Allow-Origin: http://localhost:4200`
- AND the browser does not block the response

#### Scenario: Request from an unlisted origin is rejected

- GIVEN a cross-origin request arrives with `Origin: http://attacker.example.com`
- WHEN the backend processes the request
- THEN the response does NOT include `Access-Control-Allow-Origin` for that origin
- AND the browser blocks the response per same-origin policy

---

### Requirement: HTTP Methods Allowlist

The backend MUST allow the HTTP methods actually used by the API: GET, POST, PUT,
PATCH, DELETE, and OPTIONS.

#### Scenario: Allowed methods are accepted

- GIVEN a cross-origin request with `Origin: http://localhost:4200`
- WHEN the request uses GET, POST, PUT, PATCH, or DELETE
- THEN the server processes the request and the response is accessible to the browser

---

### Requirement: Authorization Header Exposure

The backend MUST explicitly allow the `Authorization` request header in cross-origin
requests so that the Angular `AuthInterceptor` can attach Bearer tokens.

#### Scenario: Bearer token header passes preflight

- GIVEN the browser performs a CORS preflight (OPTIONS) check for a protected endpoint
- WHEN the preflight includes `Access-Control-Request-Headers: Authorization`
- THEN the preflight response includes `Authorization` in `Access-Control-Allow-Headers`
- AND the subsequent request with the `Authorization` header succeeds

---

### Requirement: CORS Preflight Handling

The backend MUST respond to HTTP OPTIONS preflight requests with the correct CORS
headers and a non-error status so the browser proceeds with the actual request.

#### Scenario: Preflight for a protected endpoint

- GIVEN the Angular app is about to issue a cross-origin request with custom headers
- WHEN the browser sends an OPTIONS preflight to any API endpoint
- THEN the response status is 200 (OK) or 204 (No Content)
- AND the response includes `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`,
  and `Access-Control-Allow-Headers`
- AND the browser proceeds to send the actual request

---

### Requirement: No Impact on Existing Backend Behavior

The CORS configuration MUST be isolated so that it does not alter any existing
authentication, authorization, or API logic.

#### Scenario: Backend integration tests unaffected

- GIVEN the backend test suite (321 tests) was passing before this change
- WHEN `CorsConfig` is added
- THEN all existing tests continue to pass
- AND no existing security rule or endpoint behavior changes

---

### Requirement: Dev-Scoped Origin (No Wildcard in Production Path)

The allowed origin `http://localhost:4200` MUST be explicit (not `*`) so that
credentials (Authorization headers) can flow correctly and the configuration does not
inadvertently open the API to all origins.

#### Scenario: Wildcard origin is NOT used

- GIVEN the CORS configuration is deployed
- WHEN the response headers are inspected for any cross-origin request
- THEN `Access-Control-Allow-Origin` is never set to `*`
