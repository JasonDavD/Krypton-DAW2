# Frontend HTTP & Auth Specification

## Purpose

Defines behavior for the Angular HTTP layer and authentication domain: interceptors,
`AuthService`, and route guards. All downstream feature changes rely on this contract.

## Requirements

### Requirement: Bearer Token Attachment

The system MUST attach an `Authorization: Bearer <token>` header to every outgoing
HTTP request when a token is present in storage.

The system MUST NOT attach the header for public auth endpoints (login, register).

#### Scenario: Authenticated request

- GIVEN a valid token exists in storage
- WHEN the application issues any HTTP request to a protected API endpoint
- THEN the request headers contain `Authorization: Bearer <token>`

#### Scenario: Public endpoint bypass

- GIVEN a valid token exists in storage
- WHEN the application issues an HTTP request to the login or register endpoint
- THEN the `Authorization` header is NOT attached to that request

#### Scenario: No token present

- GIVEN no token is stored
- WHEN the application issues any HTTP request
- THEN the `Authorization` header is NOT added

---

### Requirement: 401 Error Handling

The system MUST react to a 401 HTTP response by clearing all auth state and redirecting
the user to the login page.

#### Scenario: Session expiry

- GIVEN a user is authenticated and their token has expired
- WHEN the API returns a 401 response
- THEN auth storage is cleared
- AND `AuthService.currentUser` and `AuthService.role` signals are reset to null
- AND the router navigates to `/auth/login`

---

### Requirement: 403 Error Handling

The system MUST react to a 403 HTTP response in a way that communicates to the user
that access is forbidden, without logging them out.

#### Scenario: Insufficient permissions

- GIVEN a user is authenticated but lacks the required role
- WHEN the API returns a 403 response
- THEN the user is NOT redirected to login
- AND auth state is NOT cleared
- AND the application signals a forbidden error (e.g., redirect to a 403 view or
  surface an error notification — exact UX is a DESIGN decision)

---

### Requirement: AuthService Login

`AuthService.login` MUST send credentials to the auth endpoint, on success persist the
received token, and populate `currentUser` and `role` signals.

#### Scenario: Successful login

- GIVEN valid credentials are submitted
- WHEN the auth endpoint returns a successful response with a token and user data
- THEN the token is persisted to storage
- AND `AuthService.currentUser` signal reflects the authenticated user
- AND `AuthService.role` signal reflects the user's role

#### Scenario: Failed login

- GIVEN invalid credentials are submitted
- WHEN the auth endpoint returns an error response
- THEN no token is persisted
- AND `AuthService.currentUser` remains null
- AND the error is propagated to the caller

---

### Requirement: AuthService Register

`AuthService.register` MUST send registration data to the register endpoint and, on
success, populate `currentUser` and `role` signals (same post-condition as login).

#### Scenario: Successful registration

- GIVEN valid registration data is submitted
- WHEN the register endpoint returns a successful response with a token and user data
- THEN the token is persisted to storage
- AND `AuthService.currentUser` and `AuthService.role` signals are set

#### Scenario: Failed registration (duplicate email)

- GIVEN registration data with an already-used email is submitted
- WHEN the register endpoint returns an error response
- THEN no token is persisted
- AND auth signals remain null
- AND the error is propagated to the caller

---

### Requirement: AuthService Logout

`AuthService.logout` MUST clear the stored token and reset `currentUser` and `role`
signals to null.

#### Scenario: Logout clears state

- GIVEN a user is authenticated (token present, signals set)
- WHEN `logout` is called
- THEN the token is removed from storage
- AND `AuthService.currentUser` signal is null
- AND `AuthService.role` signal is null

---

### Requirement: AuthGuard — Unauthenticated Access Prevention

`AuthGuard` MUST block navigation to any protected route when no authenticated user
is present, redirecting to `/login`.

#### Scenario: Unauthenticated access attempt

- GIVEN no user is authenticated
- WHEN the router attempts to activate a guarded route
- THEN navigation is cancelled
- AND the router redirects to `/auth/login`

#### Scenario: Authenticated access allowed

- GIVEN a user is authenticated
- WHEN the router attempts to activate a guarded route
- THEN navigation proceeds normally

---

### Requirement: AdminGuard — Role-Based Access Control

`AdminGuard` MUST block navigation to admin routes for any user whose `role` signal
value is not strictly equal to `'ADMIN'`.

The system MUST compare against the raw enum value `'ADMIN'`, NOT `'ROLE_ADMIN'`.

#### Scenario: Admin access granted

- GIVEN `AuthService.role` equals `'ADMIN'`
- WHEN the router attempts to activate an admin-guarded route
- THEN navigation proceeds normally

#### Scenario: Client role blocked from admin

- GIVEN `AuthService.role` equals `'CLIENTE'` (or any value other than `'ADMIN'`)
- WHEN the router attempts to activate an admin-guarded route
- THEN navigation is cancelled
- AND the user is redirected (exact destination is a DESIGN decision — e.g., `/` or `/403`)

#### Scenario: Unauthenticated user blocked from admin

- GIVEN no user is authenticated (`AuthService.role` is null)
- WHEN the router attempts to activate an admin-guarded route
- THEN navigation is cancelled
- AND the user is redirected to `/auth/login`
