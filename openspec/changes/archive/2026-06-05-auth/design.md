# Design: Auth (Authentication + User Management)

## Technical Approach

Spring Security 6 con cadena **stateless** (`SessionCreationPolicy.STATELESS`, CSRF off
por ser API sin cookies). El password se hashea con **BCrypt** (`BCryptPasswordEncoder`,
strength 10). El login emite un **JWT HS256** firmado con un secreto simétrico leído de
config/entorno. Un `JwtAuthenticationFilter` (`OncePerRequestFilter`) valida el token en
cada request y **carga el usuario** vía `CustomUserDetailsService`, mapeando `active`→
`enabled`; un usuario inactivo es rechazado al instante (resuelve el gotcha del token
vigente). La autorización por rol se aplica con `requestMatchers(...).hasRole("ADMIN")`
sobre `/api/admin/**`. El guard del último admin vive en la **capa de servicio**.

## Architecture Decisions

| Decisión | Elección | Rationale |
|---|---|---|
| Librería JWT | **jjwt** `io.jsonwebtoken` 0.12.x | Estándar de facto, API limpia, mantenida |
| Algoritmo | **HS256** (secreto simétrico) | Lean; no necesitamos par de claves (RS256) para un solo backend |
| Secreto | `${JWT_SECRET:...}` en `application.yml` (env override) | NO se commitea el real; dev usa default, prod inyecta por env. HS256 exige ≥256 bits |
| Vencimiento | 24h, sin refresh | Alcance lean; vencido → re-login |
| Hashing | `BCryptPasswordEncoder` strength 10 | Estándar, salting incorporado |
| Efecto de la baja | Filtro **carga el usuario** y exige `active` | Baja con efecto inmediato pese a token stateless; 1 lectura DB/request (ok a esta escala) |
| Autorización admin | `requestMatchers("/api/admin/**").hasRole("ADMIN")` | Declarativo en `SecurityConfig`; simple y auditable |
| Guard último admin | En `UserServiceImpl` (no en el controller ni la DB) | Regla de negocio → capa de servicio, testeable con Mockito |
| Baja de usuarios | **Soft delete** (`active`), nunca DELETE físico | FK desde `orders`/`cart`: borrar rompe el historial de ventas |
| Errores de auth | `AuthenticationEntryPoint` (401) + `AccessDeniedHandler` (403) → JSON | Respuestas consistentes, no el HTML por defecto |

## Token — estructura

```
Header:  { "alg": "HS256", "typ": "JWT" }
Payload: { "sub": "<email>", "role": "ADMIN" | "CLIENTE",
           "iat": <epoch>, "exp": <iat + 24h> }
Firma:   HMAC-SHA256(base64(header).base64(payload), JWT_SECRET)
```

El `role` viaja en el token, pero la **autorización real** se reconstruye cargando el
usuario en el filtro (fuente de verdad = DB), no confiando ciegamente en el claim.

## Data Flow — login

```
POST /api/auth/login {email, password}
   │
   ▼
AuthController → AuthService.login(...)
   │   UserRepository.findByEmail(email)  ── no existe ──▶ 401
   │   passwordEncoder.matches(raw, hash) ── no ─────────▶ 401
   │   user.active == false ─────────────────────────────▶ 401
   ▼
JwtService.generateToken(user) ──▶ AuthResponse { token, "Bearer", 86400 }  → 200
```

## Data Flow — request protegida + baja inmediata

```
GET /api/admin/users   Authorization: Bearer <jwt>
   │
   ▼
JwtAuthenticationFilter
   │  JwtService.isValid(token)? ── no/expirado ──▶ 401 (EntryPoint)
   │  email = extractEmail(token)
   │  userDetails = CustomUserDetailsService.load(email)   // lee DB
   │      user.active == false ──▶ DisabledException ──▶ 401   ← baja inmediata
   │  set SecurityContext (authorities: ROLE_ADMIN/ROLE_CLIENTE)
   ▼
SecurityConfig: /api/admin/** requiere hasRole(ADMIN)
   │   rol != ADMIN ──▶ 403 (AccessDeniedHandler)
   ▼
AdminUserController → 200
```

## File Changes

| File | Action | Description |
|---|---|---|
| `pom.xml` | Mod | + `io.jsonwebtoken:jjwt-api/impl/jackson` 0.12.x |
| `model/User.java` | Mod | + `Boolean active` (`@Column(nullable=false)`) |
| `repository/UserRepository.java` | Mod | `findByEmail`, `existsByEmail`, `countByRoleAndActiveTrue(Role)` |
| `db/migration/V2__add_user_active.sql` | Create | `ALTER TABLE users ADD active ...` |
| `db/migration/V3__seed_admin.sql` | Create | `INSERT` del ADMIN (hash BCrypt) |
| `config/SecurityConfig.java` | Create | `SecurityFilterChain`, `PasswordEncoder`, `AuthenticationManager` |
| `security/JwtService.java` | Create | generar/validar token, extraer claims |
| `security/JwtAuthenticationFilter.java` | Create | `OncePerRequestFilter` |
| `security/CustomUserDetailsService.java` | Create | `UserDetailsService` por email, `active`→`enabled` |
| `security/{RestAuthEntryPoint,RestAccessDeniedHandler}.java` | Create | 401/403 en JSON |
| `service/AuthService.java` + `impl/AuthServiceImpl.java` | Create | register, login |
| `service/UserService.java` + `impl/UserServiceImpl.java` | Create | list, create, changeRole, setStatus + guard último admin |
| `controller/AuthController.java` | Create | `/api/auth/register`, `/login` |
| `controller/AdminUserController.java` | Create | `/api/admin/users**` |
| `dto/request/*` | Create | `RegisterRequest`, `LoginRequest`, `CreateUserRequest`, `UpdateRoleRequest`, `UpdateStatusRequest` |
| `dto/response/*` | Create | `AuthResponse`, `UserResponse` |
| `mapper/UserMapper.java` | Create | `User` ↔ `UserResponse` |
| `exception/{DuplicateEmailException,LastAdminException}.java` + `GlobalExceptionHandler` | Create/Mod | 409 / 422 + 401/403 |

## Interfaces / Contracts

### Endpoints

| Método | Path | Auth | Body | OK | Errores |
|---|---|---|---|---|---|
| POST | `/api/auth/register` | público | `RegisterRequest` | 201 `UserResponse` | 409, 400 |
| POST | `/api/auth/login` | público | `LoginRequest` | 200 `AuthResponse` | 401, 400 |
| GET | `/api/admin/users` | ADMIN | — | 200 `UserResponse[]` | 401, 403 |
| POST | `/api/admin/users` | ADMIN | `CreateUserRequest` | 201 `UserResponse` | 409, 400, 403 |
| PATCH | `/api/admin/users/{id}/role` | ADMIN | `UpdateRoleRequest` | 200 `UserResponse` | 422, 404, 403 |
| PATCH | `/api/admin/users/{id}/status` | ADMIN | `UpdateStatusRequest` | 200 `UserResponse` | 422, 404, 403 |

### DTOs

```
RegisterRequest   { name, email, password }              // @NotBlank, @Email
LoginRequest      { email, password }
AuthResponse      { token, tokenType="Bearer", expiresIn=86400 }
CreateUserRequest { name, email, password, role }         // role ∈ {CLIENTE, ADMIN}
UpdateRoleRequest { role }                                // @NotNull
UpdateStatusRequest { active }                            // @NotNull Boolean
UserResponse      { id, name, email, role, active, createdAt }   // sin password
```

### JwtService (interfaz)

```java
String  generateToken(User user);     // sub=email, role, exp=+24h
boolean isValid(String token);        // firma + expiración
String  extractEmail(String token);   // claim sub
```

### SQL

```sql
-- V2__add_user_active.sql
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- V3__seed_admin.sql   (hash generado offline con BCrypt strength 10)
INSERT INTO users (name, email, password, role, active, created_at)
VALUES ('Admin Krypton', 'admin@krypton.pe',
        '$2a$10$....<hash BCrypt de la pass dev>....',
        'ADMIN', TRUE, now());
```

> El hash del seed se genera una vez con el **mismo** `BCryptPasswordEncoder(10)` (un
> pequeño main o test util) y se pega en `V3`. Credenciales dev documentadas; en prod se
> cambian. Un test de integración verifica que el ADMIN sembrado puede loguearse.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | `JwtService` genera/valida/expira/firma-mala | JUnit 5 (secreto de test fijo) |
| Unit | `AuthServiceImpl`: register ok, email dup, login ok/mal/inactivo | Mockito (repo + encoder + jwt mockeados) |
| Unit | `UserServiceImpl`: create, changeRole, setStatus, **guard último admin** | Mockito (`countByRoleAndActiveTrue`) |
| Web slice | `AuthController`, `AdminUserController`: status + contrato JSON | `@WebMvcTest` + `@MockBean` services |
| Integration | borde de seguridad: público sin token, protegido 401, admin 403/200, seed loguea, baja inmediata | `@SpringBootTest` + Testcontainers `postgres:16` |

## Security Considerations

- **Secreto JWT fuera del repo**: `application.yml` usa `${JWT_SECRET:dev-only-default-...}`.
  El valor real de prod va por variable de entorno. El `.gitignore` ya cubre `*.env`.
- El claim `role` no es la última palabra: la autorización se reconstruye desde la DB
  (usuario activo + rol real) en cada request.
- `register` jamás acepta `role`: evita escalada de privilegios.

## Migration / Rollout

`V2` (aditiva, `DEFAULT TRUE`) y `V3` (seed) se aplican al arrancar. Sin migración de
datos destructiva. Rollback: `git revert` + `docker compose down -v`.

## Open Questions

- [ ] Credenciales exactas del ADMIN seed (email/pass dev) — definir al implementar `V3`.
- [ ] ¿Endpoint para reactivar incluido en `/status` (un solo PATCH con `active`)? → sí, decidido: un único `PATCH /status`.
