# Tasks: Auth (Authentication + User Management)  ✅ COMPLETO (7 fases) — 42/42 verde

> Strict TDD activo. Test runner: **`cd backend && ./mvnw test`**.
> Donde aplica: RED (test que falla) → GREEN (implementar) → REFACTOR.
> Base package `pe.com.krypton`. Capas con interfaces. Nunca exponer `@Entity`.

## Phase 1: Infraestructura (deps + config)

- [x] 1.1 `pom.xml`: agregar `io.jsonwebtoken:jjwt-api`, `jjwt-impl` (runtime), `jjwt-jackson` (runtime) v0.12.x. ✅ `jjwt.version=0.12.6`.
- [x] 1.2 `application.yml`: bloque `app.jwt` → `secret: ${JWT_SECRET:dev-only-change-me-...≥32chars}`, `expiration: 86400000` (ms). NO commitear secreto real. ✅

## Phase 2: Schema delta — `users.active` (RED → GREEN)

- [x] 2.1 **RED**: `SchemaIntegrationTest.v2_adds_active_column_to_users` → falló (columna ausente). ✅
- [x] 2.2 **GREEN**: `db/migration/V2__add_user_active.sql` → `ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;`. ✅
- [x] 2.3 `model/User.java`: `private boolean active = true;` (primitivo + default → un usuario nace activo; el DEFAULT de la DB NO cubre inserts JPA). ✅
- [x] 2.4 `repository/UserRepository.java`: `findByEmail`, `existsByEmail`, `countByRoleAndActiveTrue(Role)`. ✅
  - Suite completa 7/7 verde (BUILD SUCCESS).

## Phase 3: Seguridad core + JWT (RED → GREEN)

- [x] 3.1 **RED**: `JwtServiceTest` (5 tests, secreto fijo): genera/valida/expirado/firma-mala/basura. (RED: no compilaba sin la clase.) ✅
- [x] 3.2 **GREEN**: `security/JwtService` (jjwt 0.12 HS256): `generateToken(User)`, `isValid`, `extractEmail`. **JwtServiceTest 5/5**. ✅
- [x] 3.3 `security/CustomUserDetailsService`: por email; authorities `"ROLE_" + role.name()`; `disabled(!active)`. ✅
- [x] 3.4 `security/JwtAuthenticationFilter` (`OncePerRequestFilter`): Bearer, valida, **carga usuario**, si `!enabled` no autentica (→401); setea `SecurityContext`. ✅
- [x] 3.5 `config/SecurityConfig`: `BCryptPasswordEncoder`, `AuthenticationManager`, `SecurityFilterChain` stateless (CSRF off); `permitAll` auth+Swagger; `hasRole("ADMIN")` admin; resto authenticated; filtro antes de `UsernamePasswordAuthenticationFilter`. ✅
- [x] 3.6 `security/RestAuthEntryPoint` (401 JSON) + `security/RestAccessDeniedHandler` (403 JSON). ✅
  - Suite completa **12/12** verde (contexto levanta con seguridad).

## Phase 4: Authentication — register / login (RED → GREEN)

- [x] 4.1 DTOs: `RegisterRequest`, `LoginRequest` (records, `@NotBlank`/`@Email`), `AuthResponse`, `UserResponse`, `mapper/UserMapper`. ✅
- [x] 4.2 `exception/{DuplicateEmailException,InvalidCredentialsException,ApiError}` + `GlobalExceptionHandler`: 409, 401, 400. ✅
- [x] 4.3 **RED**: `AuthServiceImplTest` (5 tests Mockito): register ok/dup, login ok/mal/inactivo. (RED: faltaba impl.) ✅
- [x] 4.4 **GREEN**: `service/AuthService` + `impl/AuthServiceImpl`. **5/5**. ✅
- [x] 4.5 `controller/AuthController`: `POST /register` (201), `POST /login` (200). ✅
- [x] 4.6 `@WebMvcTest AuthControllerTest` (5 tests): 201/409/400/200/401. ✅
  - GOTCHA: `@WebMvcTest` incluye beans `Filter` → arrastra `JwtAuthenticationFilter` y rompe el slice. Fix: `excludeFilters` + `addFilters=false`.
  - Suite completa **22/22** verde.

## Phase 5: User Management — admin (RED → GREEN)

- [x] 5.1 DTOs: `CreateUserRequest`, `UpdateRoleRequest`, `UpdateStatusRequest` (records). `UserResponse`/`UserMapper` ya existían (Phase 4). ✅
- [x] 5.2 `exception/{LastAdminException(422),ResourceNotFoundException(404)}` + handlers. ✅
- [x] 5.3 **RED**: `UserServiceImplTest` (8 tests Mockito): listar, crear ADMIN/dup, promover, **guard degradar/desactivar último admin → 422**, baja, not-found. ✅
- [x] 5.4 **GREEN**: `service/UserService` + `impl/UserServiceImpl` (guard: `isLastActiveAdmin` con `countByRoleAndActiveTrue`). **8/8**. ✅
- [x] 5.5 `controller/AdminUserController`: GET/POST `/api/admin/users`, PATCH `/{id}/role`, PATCH `/{id}/status`. ✅
- [x] 5.6 `@WebMvcTest AdminUserControllerTest` (7 tests): 200/201/409/422/400 + contratos (excludeFilters reusado). **401/403 → Phase 6 integración** (donde corresponde el borde de seguridad). ✅
  - Suite completa **37/37** verde.

## Phase 6: Seed admin + integración del borde (RED → GREEN)

- [x] 6.1 Hash BCrypt(10) generado offline (test throwaway, borrado); `V3__seed_admin.sql` → INSERT ADMIN (`admin@krypton.pe` / `Admin123!`, `active=true`). ✅
- [x] 6.2 **Integración** `AuthSecurityIntegrationTest`: público sin token 201; protegido sin token 401; `/api/admin/**` CLIENTE 403. ✅
- [x] 6.3 **Integración**: ADMIN sembrado loguea y accede a `/api/admin/users` (200). ✅
- [x] 6.4 **Integración**: baja inmediata — admin2 desactivado → su token vigente da 401. ✅
- [x] 6.5 `cd backend && ./mvnw test` → **42/42 verde, BUILD SUCCESS**. (Fix: `KryptonApplicationTests` asumía `users` vacía; ahora `isPositive()` por el seed.) ✅

## Phase 7: Tooling

- [x] 7.1 `openspec/config.yaml`: `test_command`/`command`/`build_command` → `cd backend && ./mvnw ...` (tras el monorepo). ✅
