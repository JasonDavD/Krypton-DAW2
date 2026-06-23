# Tasks: Frontend Foundation — Angular SPA Skeleton

> SDD TASKS phase. Sources: spec #349, design #350. Artifact store: hybrid. Date: 2026-06-15.
> Execution order: P1 → P2 → P3 (sequential within phase) → P4 ∥ P5 (phases 4 and 5 are independent) → P6.

---

## Phase 1 — Infrastructure / Scaffold

> No RED-GREEN here. Goal: repo skeleton compiles, `npm test` runs an empty suite green.

### [x] P1.1 — Generate Angular 18 standalone project
**Spec**: frontend-scaffold › Application Bootstrap  
**Action**: From `Krypton-Ecommerce/`, run `ng new frontend --standalone --style=scss --routing=false --skip-git --skip-install` (Angular 18.x pinned in package.json after install).  
**Done when**: `frontend/src/main.ts` exists with `bootstrapApplication`, no `NgModule` decorator anywhere.  
**Sequential**: must come first.
**Completed**: `npx @angular/cli@18 new krypton-frontend --directory . --style=scss --ssr=false --skip-git --package-manager npm`. Confirmed `bootstrapApplication` in `src/main.ts`.

### [x] P1.2 — Install dependencies and pin Angular 18
**Spec**: frontend-scaffold › Application Bootstrap  
**Action**: Inside `frontend/`, run `npm install`. Then verify `@angular/core` version in `package.json` is `^18.x.x`. Lock major: ensure no `^19` drift.  
**Done when**: `node_modules/` populated, `package.json` shows `"@angular/core": "^18`.  
**Depends on**: P1.1.
**Completed**: `@angular/core: ^18.2.0` confirmed. node_modules populated.

### [x] P1.3 — Replace Karma/Jasmine with Jest (jest-preset-angular)
**Spec**: frontend-scaffold › Application Bootstrap (test infrastructure)  
**Design**: Resolved Decision 1 — Jest as test runner.  
**Action**:
1. `npm install --save-dev jest jest-preset-angular @types/jest`
2. Remove `karma.conf.js`, `src/test.ts` if they exist; remove `@angular-devkit/build-angular:karma` from `angular.json`.
3. Create `jest.config.ts` with `preset: 'jest-preset-angular'` and `setupFilesAfterFramework: ['<rootDir>/setup-jest.ts']`.
4. Create `setup-jest.ts` with `import 'jest-preset-angular/setup-jest';`.
5. Add `"test": "jest"` to `package.json` scripts (replaces `ng test`).
6. Run `npm test` — must exit 0 with "Test Suites: 0 passed" (empty suite green).  
**Done when**: `npm test` exits 0 with no Karma reference.  
**Depends on**: P1.2.
**Completed**: Installed jest@^29, jest-preset-angular@^14.6.2 (pinned to 14.x for Angular 18 compat), @types/jest@^29, ts-node. Removed karma/jasmine devDeps. Updated tsconfig.spec.json types to ["jest"]. setup-jest.ts uses `setupZoneTestEnv()` (modern API). `npm test` exits 0, 3 tests pass, no Karma reference.
**Deviation**: Tasks said `jest-preset-angular` (latest) but v16 requires Angular 19+. Pinned to `^14.6.2` for Angular 18 compatibility. `setupFilesAfterEnv` used (not `setupFilesAfterFramework` which is a typo in tasks). Modern `setupZoneTestEnv()` API used instead of deprecated `import 'jest-preset-angular/setup-jest'`.

### [x] P1.4 — Create environment files
**Spec**: frontend-scaffold › Environment Configuration  
**Action**:
1. Create `src/environments/environment.ts`:
   ```ts
   export const environment = {
     production: false,
     apiBaseUrl: 'http://localhost:8080'
   };
   ```
2. Create `src/environments/environment.prod.ts` with `production: true, apiBaseUrl: 'http://localhost:8080'` (prod URL TBD, non-blocking).
3. Reference `environment.apiBaseUrl` in at least one placeholder to confirm compile-time check works.  
**Done when**: `ng build` (or `ng build --watch`) resolves environment import without error.  
**Depends on**: P1.1. Can run in parallel with P1.3.
**Completed**: Both environment files created. `app.config.ts` imports environment and exports `API_BASE_URL = environment.apiBaseUrl` as compile-time placeholder. `npm test` still exits 0 after this change.

---

## Phase 2 — Contract Types (DTO Model Interfaces)

> Pure TypeScript, no test runner required. All tasks in this phase can run in parallel once P1.4 is done.

### [x] P2.1 — Auth models (`src/app/models/auth.model.ts`)
**Spec**: frontend-scaffold › DTO Model Interfaces › auth  
**Design**: Type Mapping — Role → `'CLIENTE' | 'ADMIN'`  
**Interfaces**: `LoginRequest`, `RegisterRequest`, `AuthResponse { token: string }`, `UserResponse { id: number; email: string; firstName: string; lastName: string; role: Role; createdAt: string /* ISO 8601 Instant */ }`, `Role = 'CLIENTE' | 'ADMIN'`.  
**Done when**: `tsc --noEmit` passes with no errors referencing this file.  
**Depends on**: P1.4.
**Completed**: Created `auth.model.ts`. Interfaces: `Role`, `LoginRequest`, `RegisterRequest`, `CreateUserRequest`, `UpdateRoleRequest`, `UpdateStatusRequest`, `AuthResponse`, `UserResponse`. Grounded against real backend records. `UserResponse` has `name` (not `firstName`/`lastName` — backend uses single `name` field). `tsc --noEmit` clean.

### [x] P2.2 — Product & Category models (`src/app/models/product.model.ts`, `category.model.ts`)
**Spec**: frontend-scaffold › DTO Model Interfaces › product, category  
**Design**: BigDecimal → `number` with precision comment; Instant → `string`.  
**Interfaces**: `Category { id: number; name: string }`. `ProductResponse { id: number; name: string; description: string; /** BigDecimal — IEEE-754 double; may lose precision for large values */ price: number; stock: number; imageUrl: string; category: Category; createdAt: string }`. `ProductRequest { name: string; description: string; price: number; stock: number; imageUrl: string; categoryId: number }`.  
**Done when**: `tsc --noEmit` clean.  
**Depends on**: P1.4. Parallel with P2.1.
**Completed**: Created `product.model.ts` (no separate `category.model.ts` — design says flat models/, category types co-located). Interfaces: `CategoryRequest`, `CategoryResponse`, `ProductRequest`, `ProductResponse`, `PageResponse<T>`. `ProductResponse` has `categoryId + categoryName` (flat, NOT nested object) — mirrors real backend DTO. `tsc --noEmit` clean.

### [x] P2.3 — Cart & CartItem models (`src/app/models/cart.model.ts`)
**Spec**: frontend-scaffold › DTO Model Interfaces › cart, cart-item  
**Interfaces**: `CartItemResponse { id: number; product: ProductResponse; quantity: number; /** BigDecimal */ unitPrice: number }`. `CartResponse { id: number; user: UserResponse; items: CartItemResponse[]; /** BigDecimal */ total: number }`. `AddToCartRequest { productId: number; quantity: number }`.  
**Done when**: `tsc --noEmit` clean.  
**Depends on**: P2.2. Sequential within P2.
**Completed**: Created `cart.model.ts`. Interfaces: `CartItemRequest`, `UpdateQuantityRequest`, `CartItemResponse`, `CartResponse`. Shape surprises vs tasks description: `CartItemResponse` has `itemId/productId/productName/sku/price/quantity/subtotal` (flat, not nested product). `CartResponse` has `cartId/items/total/updatedAt`. `tsc --noEmit` clean.

### [x] P2.4 — Order & OrderItem models (`src/app/models/order.model.ts`)
**Spec**: frontend-scaffold › DTO Model Interfaces › order, order-item  
**Design**: `OrderStatus → 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA'`  
**Interfaces**: `OrderStatus = 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA'`. `OrderItemResponse { id: number; product: ProductResponse; quantity: number; /** BigDecimal */ unitPrice: number; /** BigDecimal */ subtotal: number }`. `OrderResponse { id: number; user: UserResponse; items: OrderItemResponse[]; /** BigDecimal */ total: number; status: OrderStatus; orderDate: string; createdAt: string }`. `UpdateOrderStatusRequest { status: OrderStatus }`.  
**Done when**: `tsc --noEmit` clean.  
**Depends on**: P2.2. Parallel with P2.3.
**Completed**: Created `order.model.ts`. Interfaces: `OrderStatus`, `PaymentMethod`, `OrderStatusUpdateRequest`, `PaymentRequest`, `OrderItemResponse`, `OrderResponse`. Shape surprises: `OrderResponse` has `id/userId/orderDate/status/total/items` (flat, NOT nested user). `OrderItemResponse` has `id/productId/productName/quantity/unitPrice/subtotal` (flat). `OrderResponse.status` is `String` at the Java level (not the enum). `tsc --noEmit` clean.

### [x] P2.5 — Report & API error models (`src/app/models/report.model.ts`, `api.model.ts`)
**Spec**: frontend-scaffold › DTO Model Interfaces › report  
**Design**: `ApiError.error` string field (used by ErrorInterceptor).  
**Interfaces**: `SalesReportItem { productId: number; productName: string; totalSold: number; /** BigDecimal */ totalRevenue: number }`. `ApiError { status: number; error: string; message: string; path: string }`.  
**Done when**: `tsc --noEmit` clean.  
**Depends on**: P1.4. Parallel with P2.1–P2.4.
**Completed**: Created `report.model.ts` and `api.model.ts`. Shape surprises vs tasks description: actual backend has 4 full report types (VentasPorPeriodoReport R1, TopProductosReport R2, KardexReport R3, OrdenesListadoReport R4) — NOT a single `SalesReportItem`. Also added `MovementType` enum. `ApiError` has only `{status, error}` — NOT `message` or `path` fields (tasks were inaccurate; grounded against real `ApiError.java`). `tsc --noEmit` clean.

### P2.6 — Live Instant format verification task (design-mandated)
**Design**: Instant Verification section — must confirm Jackson serializes `Instant` as ISO 8601 string, not epoch ms.  
**Action**: After backend is running (can be deferred to first backend integration test), issue a GET to any endpoint returning `UserResponse` or `OrderResponse`, assert `typeof createdAt === 'string'` AND `!isNaN(new Date(createdAt).getTime())`. Document result as a comment in `user.model.ts` or `order.model.ts`. If epoch ms is found, change field type note accordingly.  
**Done when**: Manual verification completed and result documented in the model file comment.  
**Depends on**: P2.1, P2.4. Can be deferred until backend is reachable (does NOT block P3).

---

## Phase 3 — HTTP / Auth Core (Strict TDD — RED → GREEN for every unit)

> Each logic unit: write failing spec FIRST, run `npm test` to confirm RED, then implement to GREEN. No exceptions.

### [x] P3.1 — AuthService RED (failing spec)
**Spec**: frontend-http-auth › AuthService Login, Register, Logout  
**Action**: Create `src/app/core/auth/auth.service.spec.ts`. Write failing tests for:
- `login()`: sends POST `/api/auth/login`, on success sets `currentUser` + `role` signals, persists token to `localStorage`.
- `login()` failure: no token stored, signals remain null, error propagated via observable.
- `register()`: sends POST `/api/auth/register`, same success post-conditions as login.
- `register()` failure: no token, signals null, error propagated.
- `logout()`: removes token from `localStorage`, resets signals to null.
- `isAuthenticated()` computed: true when `currentUser` is non-null.
- `isAdmin()` computed: true when `role === 'ADMIN'`.
- Boot rehydration: if valid unexpired token in `localStorage` on construction, signals populated from decoded claims.
Use `HttpTestingController` for HTTP assertions, mock `localStorage` via `spyOn(localStorage, ...)`.  
**Done when**: `npm test` exits with these tests in FAILED state (red).  
**Depends on**: P1.3 (Jest running).

### [x] P3.2 — AuthService GREEN (implementation)
**Spec**: frontend-http-auth › AuthService Login, Register, Logout  
**Design**: State with Signals section; Token Persistence: localStorage; Rehydration on boot.  
**Action**: Create `src/app/core/auth/auth.service.ts` (`@Injectable({ providedIn: 'root' })`):
- Private writable signals `_currentUser`, `_role`; expose as `currentUser.asReadonly()`, `role.asReadonly()`.
- `isAuthenticated = computed(() => this._currentUser() !== null)`.
- `isAdmin = computed(() => this._role() === 'ADMIN')`.
- `login(req: LoginRequest): Observable<AuthResponse>`: POST, tap → decode JWT claims, set signals, `localStorage.setItem('token', token)`.
- `register(req: RegisterRequest): Observable<AuthResponse>`: same tap.
- `logout()`: `localStorage.removeItem('token')`, set signals to null.
- Constructor: read `localStorage.getItem('token')`, if present + `exp > Date.now()/1000`, decode and populate signals.  
**Done when**: `npm test` passes all tests from P3.1 (green). Gate: `npm test` exits 0.  
**Depends on**: P3.1.

### [x] P3.3 — AuthInterceptor RED (failing spec)
**Spec**: frontend-http-auth › Bearer Token Attachment  
**Action**: Create `src/app/core/interceptors/auth.interceptor.spec.ts`. Write failing tests for:
- Adds `Authorization: Bearer <token>` to requests when token in storage.
- Does NOT add header for `/api/auth/login`.
- Does NOT add header for `/api/auth/register`.
- Does NOT add header when no token.  
**Done when**: `npm test` exits with these tests FAILED (red).  
**Depends on**: P3.2.

### [x] P3.4 — AuthInterceptor GREEN (implementation)
**Spec**: frontend-http-auth › Bearer Token Attachment  
**Design**: Functional Interceptors — `authInterceptor`; URL match `/api/auth/**` → bypass.  
**Action**: Create `src/app/core/interceptors/auth.interceptor.ts` as `HttpInterceptorFn`. If URL includes `/api/auth/`, call `next(req)` unmodified. Otherwise read token from `localStorage`; if present, clone request adding `Authorization: Bearer ${token}` header.  
**Done when**: `npm test` passes all P3.3 tests (green). Gate: `npm test` exits 0.  
**Depends on**: P3.3.

### [x] P3.5 — ErrorInterceptor RED (failing spec)
**Spec**: frontend-http-auth › 401 Error Handling, 403 Error Handling  
**Action**: Create `src/app/core/interceptors/error.interceptor.spec.ts`. Write failing tests for:
- 401 response: calls `AuthService.logout()`, calls `Router.navigate(['/auth/login'])`.
- 403 response: does NOT call `AuthService.logout()`, does NOT navigate, error observable is emitted (surfaces forbidden).
- Other errors (e.g., 500): re-thrown, no special handling.  
**Done when**: `npm test` exits with these tests FAILED (red).  
**Depends on**: P3.2.

### [x] P3.6 — ErrorInterceptor GREEN (implementation)
**Spec**: frontend-http-auth › 401 Error Handling, 403 Error Handling  
**Design**: Functional Interceptors — `errorInterceptor`; `catchError` branch on `error.status`.  
**Action**: Create `src/app/core/interceptors/error.interceptor.ts` as `HttpInterceptorFn`. Use `catchError((error: HttpErrorResponse) => { if (error.status === 401) { authService.logout(); router.navigate(['/auth/login']); } if (error.status === 403) { /* surface notification */ } return throwError(() => error); })`.  
**Done when**: `npm test` passes all P3.5 tests (green). Gate: `npm test` exits 0.  
**Depends on**: P3.5.

### [x] P3.7 — AuthGuard RED (failing spec)
**Spec**: frontend-http-auth › AuthGuard — Unauthenticated Access Prevention  
**Action**: Create `src/app/core/guards/auth.guard.spec.ts`. Write failing tests for:
- `isAuthenticated()` is false → returns `UrlTree` pointing to `/auth/login`.
- `isAuthenticated()` is true → returns `true`.
Use `TestBed.runInInjectionContext`.  
**Done when**: `npm test` exits with these tests FAILED (red).  
**Depends on**: P3.2.

### [x] P3.8 — AuthGuard GREEN (implementation)
**Spec**: frontend-http-auth › AuthGuard  
**Design**: Functional Guards (CanActivateFn).  
**Action**: Create `src/app/core/guards/auth.guard.ts` as `CanActivateFn`. `inject(AuthService).isAuthenticated() ? true : inject(Router).createUrlTree(['/auth/login'])`.  
**Done when**: `npm test` passes all P3.7 tests (green). Gate: `npm test` exits 0.  
**Depends on**: P3.7.

### [x] P3.9 — AdminGuard RED (failing spec)
**Spec**: frontend-http-auth › AdminGuard — Role-Based Access Control  
**Action**: Create `src/app/core/guards/admin.guard.spec.ts`. Write failing tests for:
- `role() === 'ADMIN'` → returns `true`.
- `role() === 'CLIENTE'` → returns `UrlTree` (redirect, not `/auth/login`).
- `role() === null` (unauthenticated) → returns `UrlTree` to `/auth/login`.
- Confirm comparison is strict `=== 'ADMIN'`, NOT `=== 'ROLE_ADMIN'`.  
**Done when**: `npm test` exits with these tests FAILED (red).  
**Depends on**: P3.2.

### [x] P3.10 — AdminGuard GREEN (implementation)
**Spec**: frontend-http-auth › AdminGuard  
**Design**: Functional Guards; RAW 'ADMIN' comparison.  
**Action**: Create `src/app/core/guards/admin.guard.ts` as `CanActivateFn`. Check `isAuthenticated()` first; then `role() === 'ADMIN'`; redirect unauthenticated to `/auth/login`, redirect CLIENTE to `/` (or `/403` stub).  
**Done when**: `npm test` passes all P3.9 tests (green). Gate: `npm test` exits 0.  
**Depends on**: P3.9.

### [x] P3.11 — Wire interceptors + guards into app.config.ts
**Spec**: frontend-scaffold › Application Bootstrap; frontend-http-auth (all)  
**Design**: `app.config.ts` — `provideHttpClient(withInterceptors([authInterceptor, errorInterceptor]))`, interceptor order auth FIRST.  
**Action**: Create/update `src/app/app.config.ts`: `provideRouter(routes)`, `provideHttpClient(withInterceptors([authInterceptor, errorInterceptor]))`. Confirm `authInterceptor` is listed before `errorInterceptor`.  
**Done when**: `ng build --configuration=development` succeeds (no compile errors).  
**Depends on**: P3.4, P3.6, P3.8, P3.10.

---

## Phase 4 — Shell + Routing (parallel with Phase 5)

> Components: pragmatic render-tests for critical auth branches only (no forced RED-GREEN cycle).

### [x] P4.1 — FooterComponent (no test required)
**Spec**: frontend-scaffold › Application Bootstrap (shell always visible)  
**Action**: Create `src/app/layout/footer/footer.component.ts` (standalone, SCSS). Minimal markup: `<footer>Krypton E-commerce © 2026</footer>`. No inputs/outputs.  
**Done when**: Component declares `standalone: true`, compiles clean.  
**Depends on**: P1.3. Parallel with P4.2.

### [x] P4.2 — NavbarComponent + pragmatic render spec
**Spec**: frontend-scaffold › Auth-Reactive Navbar  
**Design**: Navbar consumes `isAuthenticated()` / `isAdmin()` signals; no async pipe.  
**Action**:
1. Create `src/app/layout/navbar/navbar.component.ts` (standalone): inject `AuthService`, expose `isAuthenticated` and `isAdmin` signals in template via `@if (isAuthenticated())`.
2. Template: unauthenticated → render login + register links; authenticated → render `{{ authService.currentUser()?.email }}` + logout button.
3. Create `src/app/layout/navbar/navbar.component.spec.ts`: pragmatic render-test (NOT strict TDD):
   - Arrange `AuthService` with `isAuthenticated()` false → expect login link present, logout absent.
   - Arrange `AuthService` with `isAuthenticated()` true, `currentUser().email = 'a@b.com'` → expect email displayed, logout visible, login absent.
   - Use `TestBed.configureTestingModule` with `HttpClientTestingModule` stub for `AuthService`.  
**Done when**: `npm test` passes the Navbar spec (2 render branches).  
**Depends on**: P3.2 (AuthService exists for injection).

### [x] P4.3 — AppComponent shell (navbar + router-outlet + footer)
**Spec**: frontend-scaffold › Application Bootstrap; Navigation does not destroy shell  
**Action**: Update `src/app/app.component.ts` (standalone) to template: `<app-navbar /><router-outlet /><app-footer />`. Import `NavbarComponent`, `FooterComponent`, `RouterOutlet`.  
**Done when**: `ng build` resolves all imports cleanly.  
**Depends on**: P4.1, P4.2.

### [x] P4.4 — Route scaffold with lazy stubs + guards
**Spec**: frontend-scaffold › Lazy Stub Route Declarations  
**Design**: `app.routes.ts` — stub lazy routes; guards applied.  
**Action**: Create `src/app/app.routes.ts` with the following lazy routes (inline stub components acceptable):
- `/` → redirect to `/catalog`
- `/catalog` → lazy `CatalogStubComponent`
- `/cart` → lazy `CartStubComponent`, guarded by `authGuard`
- `/orders` → lazy `OrdersStubComponent`, guarded by `authGuard`
- `/admin` → lazy `AdminStubComponent`, guarded by `[authGuard, adminGuard]`
- `/reports` → lazy `ReportsStubComponent`, guarded by `[authGuard, adminGuard]`
- `/auth/login` → lazy `LoginStubComponent`
- `/auth/register` → lazy `RegisterStubComponent`
Each stub: a minimal standalone component with a `<p>` tag identifying the route.  
**Done when**: `ng build` compiles all routes; navigating each in browser yields stub text (no 404 in console).  
**Depends on**: P3.8, P3.10 (guards must exist).

---

## Phase 5 — Backend CORS (Strict TDD, Java — parallel with Phase 4)

> RED = failing MockMvc preflight test. GREEN = `CorsConfig.java` + one line in `SecurityConfig`. Gate: `cd backend && ./mvnw test`.

### [x] P5.1 — CORS MockMvc preflight test RED
**Spec**: cors-config › CORS Preflight Handling, Dev Origin Allowlist, Authorization Header Exposure  
**Action**: Create `backend/src/test/java/pe/com/krypton/config/CorsConfigTest.java` (JUnit 5, `@WebMvcTest` or `@SpringBootTest` + MockMvc):
```java
mockMvc.perform(options("/api/products")
    .header("Origin", "http://localhost:4200")
    .header("Access-Control-Request-Method", "GET")
    .header("Access-Control-Request-Headers", "Authorization"))
  .andExpect(status().isOk())
  .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
  .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")))
  .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
```
Also add: rejected-origin test (no `Access-Control-Allow-Origin` header in response for `http://attacker.example.com`).  
**Done when**: `cd backend && ./mvnw test` exits with `CorsConfigTest` in FAILED state (red — `CorsConfig` doesn't exist yet).  
**Depends on**: existing backend (test runner must be functional). Independent of Phase 4.

### [x] P5.2 — CorsConfig.java GREEN
**Spec**: cors-config (all requirements)  
**Design**: `CorsConfig.java` — `CorsConfigurationSource` bean; `SecurityConfig` line `.cors(Customizer.withDefaults())`.  
**Action**:
1. Create `backend/src/main/java/pe/com/krypton/config/CorsConfig.java` (`@Configuration`):
   - `@Bean CorsConfigurationSource corsConfigurationSource()`: `CorsConfiguration config = new CorsConfiguration(); config.setAllowedOrigins(List.of("http://localhost:4200")); config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS")); config.setAllowedHeaders(List.of("Authorization","Content-Type")); config.setExposedHeaders(List.of("Content-Disposition")); config.setAllowCredentials(true); UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", config); return source;`
2. Modify `backend/src/main/java/pe/com/krypton/config/SecurityConfig.java`: add `.cors(Customizer.withDefaults())` as the first call in the `http` builder chain (before `.csrf(...)`). Import `org.springframework.security.config.Customizer`.  
**Done when**: `cd backend && ./mvnw test` passes ALL tests including `CorsConfigTest` and the existing 321 tests. Gate: exit 0.  
**Depends on**: P5.1.

---

## Phase 6 — Acceptance Checklist (mapped to proposal Success Criteria)

> Run after Phases 4 and 5 both complete. Manual + automated verification pass.

### P6.1 — Full test suite green
**Action**: Run `npm test` from `frontend/`. Confirm: AuthService (login/register/logout/rehydration/computed), AuthInterceptor (3 scenarios), ErrorInterceptor (3 scenarios), AuthGuard (2 scenarios), AdminGuard (3 scenarios), NavbarComponent (2 render branches) — all pass.  
**Done when**: `npm test` exits 0, no skipped tests.

### P6.2 — Backend CORS + existing tests green
**Action**: Run `cd backend && ./mvnw test`. Confirm: `CorsConfigTest` passes (preflight 200, correct headers, rejected origin no wildcard); all 321 prior tests still pass.  
**Done when**: `./mvnw test` exits 0.

### P6.3 — Integration smoke: Angular → Spring Boot round-trip
**Action**: Start backend (`./mvnw spring-boot:run`), start frontend (`ng serve`). Open browser, open DevTools Network tab. Navigate to `http://localhost:4200`. Verify: no console errors on startup, navbar and footer visible, preflight OPTIONS for any API call returns 200 with `Access-Control-Allow-Origin: http://localhost:4200`, no wildcard.  
**Done when**: Network tab shows correct CORS headers; no `CORS policy` browser error.

### P6.4 — Live Instant format lock-in (design-mandated verification)
**Action**: With backend running, perform a login via the browser or curl. Inspect the `UserResponse.createdAt` field in the JSON response. Assert `typeof value === 'string'` AND the string parses as a valid ISO 8601 date via `new Date(value)`. If it is an epoch number, update all `Instant` type annotations in models to `number` and re-run `npm test`.  
**Done when**: Model type is confirmed correct and documented as a comment in `auth.model.ts` and `order.model.ts`.

### P6.5 — Shell navigation does not destroy layout
**Action**: In browser, navigate to `/catalog`, `/cart` (if authenticated), `/orders`, confirm navbar and footer remain present after each navigation, only the outlet content changes.  
**Done when**: Manual pass across all declared routes.

### P6.6 — Guard flows end-to-end
**Action**: Without logging in, navigate to `/cart`, `/orders`, `/admin`, `/reports`. Confirm redirect to `/auth/login` each time. Log in as CLIENTE role. Attempt `/admin`. Confirm redirect to `/` (or `/403` stub). Log in as ADMIN. Confirm `/admin` and `/reports` activate without redirect.  
**Done when**: All three role scenarios confirmed manually.

---

## Dependency Summary

```
P1.1 → P1.2 → P1.3 → P3.1..P3.10
P1.1 → P1.4 → P2.1..P2.5
P2.1,P2.2 → P2.3
P2.2 → P2.4
P3.2 → P3.3,P3.5,P3.7,P3.9 (each RED then GREEN sequential)
P3.4,P3.6,P3.8,P3.10 → P3.11
P3.2 → P4.2
P3.8,P3.10 → P4.4
P4.1,P4.2 → P4.3
P4.3,P4.4 → P6.1,P6.3,P6.5,P6.6
P5.1 → P5.2 → P6.2,P6.3

Phase 4 ∥ Phase 5 (no cross-dependency)
P6.1..P6.6 all require Phase 4 AND Phase 5 complete
```

## Parallelism Map

| Can run in parallel | Tasks |
|---------------------|-------|
| P1.3 and P1.4 | After P1.2 |
| P2.1, P2.2, P2.5 | After P1.4 |
| P2.3 and P2.4 | After P2.2 |
| Phase 4 and Phase 5 | After P3.11 (Phase 5 can start even earlier from existing backend) |
| P4.1 and P4.2 | After P3.2 |
| P6.1 and P6.2 | After respective phase complete |

**Total tasks: 28** (P1.1–P1.4 = 4, P2.1–P2.6 = 6, P3.1–P3.11 = 11, P4.1–P4.4 = 4, P5.1–P5.2 = 2, P6.1–P6.6 = 6 — minus 1 for P2.6 being deferrable = 28 including it).
