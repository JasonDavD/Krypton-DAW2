# Proposal: Auth (Authentication + User Management)

## Intent

Dar identidad y control de acceso al sistema: que un usuario pueda **registrarse**,
**loguearse** y recibir un **JWT** stateless, y que un **ADMIN** pueda **gestionar
usuarios** (crear —incluidos otros admins—, listar, cambiar rol y dar de baja lógica).
Sobre esto se apoyan todos los features siguientes (carrito por usuario, checkout,
panel de admin). Define el borde de seguridad: público vs protegido vs solo-admin.

## Scope

### In Scope

**Authentication (público / autenticado):**
- `POST /api/auth/register`: alta de usuario con rol **CLIENTE** fijo (no elegible por el cliente). Password **BCrypt**. Email único.
- `POST /api/auth/login`: valida credenciales **y que el usuario esté activo**; devuelve un **access token JWT** (vence en 24h).
- `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `SecurityConfig` stateless.
- Seed del **ADMIN** inicial vía Flyway.

**User Management (solo ADMIN, `/api/admin/users`):**
- `GET /api/admin/users`: lista usuarios (nunca el password).
- `POST /api/admin/users`: crea usuario con **rol elegible** (CLIENTE o ADMIN). Único camino para crear un ADMIN.
- `PATCH /api/admin/users/{id}/role`: cambia el rol (con **guard de último admin**).
- `PATCH /api/admin/users/{id}/status`: activa / da de baja lógica (`active`), con guard de último admin activo.

**Infra:**
- Flyway `V2`: agrega columna `active BOOLEAN NOT NULL DEFAULT true` a `users`.
- Flyway `V3`: seed del ADMIN (con hash BCrypt, `active=true`).
- DTOs: `RegisterRequest`, `LoginRequest`, `AuthResponse`, `CreateUserRequest`, `UserResponse`, `UpdateRoleRequest`, `UpdateStatusRequest`.
- Errores: 409 email duplicado, 401 credenciales inválidas / usuario inactivo, 403 sin rol, 400 validación, 422 guard del último admin.

### Out of Scope
- **Borrado físico** de usuarios → rechazado: rompe la FK desde `orders`/`cart` y destruiría el historial de ventas. Se usa baja lógica.
- **Refresh tokens** (vencido el access token, re-login).
- Recuperación de contraseña, verificación por email, OAuth social.
- Auto-edición de perfil por el propio usuario.
- Rate limiting / bloqueo por intentos fallidos.
- Frontend.

## Capabilities

### New Capabilities
- `authentication`: registro, login (verifica activo), hashing BCrypt, emisión/validación de JWT, Spring Security stateless y el borde público/protegido/rol.
- `user-management`: operaciones de administración de usuarios (listar, crear con rol, cambiar rol, baja/alta lógica), restringidas a ADMIN, con guard de último admin.

### Modified Capabilities
- `persistence-schema`: se agrega `users.active` (Flyway `V2`) → la entidad `User` suma el campo `active`.

## Approach

Spring Security 6 stateless (`SessionCreationPolicy.STATELESS`). `AuthService` y
`UserService` (interfaz + impl, según skill `krypton-backend`); BCrypt vía `PasswordEncoder`;
JWT vía `jjwt`. El `JwtAuthenticationFilter` (extiende `OncePerRequestFilter`) **carga el
usuario y rechaza si está inactivo** → la baja lógica tiene efecto inmediato pese al token
vigente. Autorización por rol con `@PreAuthorize("hasRole('ADMIN')")` (o reglas en
`SecurityConfig`) sobre `/api/admin/**`. El ADMIN nace solo por seed (`V3`) o por otro ADMIN.
Nunca se expone la entidad `User`: entran/salen DTOs.

TDD (RED→GREEN→REFACTOR): unit de `JwtService`; unit de `AuthServiceImpl` y `UserServiceImpl`
con Mockito (registro, email dup, login ok/inválido/inactivo, crear admin, cambiar rol,
guard último admin, baja lógica); `@WebMvcTest` de los controllers (status + contrato);
integración Testcontainers del borde de seguridad (público/protegido/admin) y del seed.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `backend/.../controller/{AuthController,AdminUserController}.java` | New | Endpoints auth + admin/users |
| `backend/.../service/{AuthService,UserService}.java` (+ `impl/`) | New | Lógica de auth y gestión de usuarios |
| `backend/.../security/**` | New | `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetailsService` |
| `backend/.../config/SecurityConfig.java` | New | Cadena stateless, `PasswordEncoder`, reglas por rol |
| `backend/.../dto/{request,response}/**` | New | DTOs de auth y user-management |
| `backend/.../exception/**` | New/Mod | `DuplicateEmailException`, `LastAdminException`; 401/403/409/422 en `GlobalExceptionHandler` |
| `backend/.../model/User.java` | Mod | Nuevo campo `active` |
| `backend/.../repository/UserRepository.java` | Mod | `findByEmail`, `existsByEmail`, conteo de admins activos |
| `backend/src/main/resources/db/migration/V2__add_user_active.sql` | New | Columna `active` |
| `backend/src/main/resources/db/migration/V3__seed_admin.sql` | New | Seed del ADMIN |
| `backend/pom.xml` | Mod | Dependencia `jjwt` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Secret del JWT hardcodeado / en el repo | Med | Leerlo de config/env (placeholder en `application.yml`), NO commitear el real |
| Baja lógica sin efecto por token vigente (stateless) | Med | El filtro carga el usuario y rechaza si `!active` → efecto inmediato |
| **Lockout**: degradar/desactivar al último ADMIN | Med | Guard en `UserService`: no permitir si dejaría 0 admins activos (test dedicado) |
| Filtro mal ubicado → endpoints abiertos o todo bloqueado | Med | Test integración: público sin token, protegido 401, admin sin rol 403 |
| Hash del ADMIN en `V3` no matchea el `PasswordEncoder` | Low | Generar el hash con el mismo BCrypt; test que loguea al admin sembrado |
| `users.active` rompe `ddl-auto: validate` o tests previos | Low | Entidad + `V2` juntas; `DEFAULT true` no afecta filas existentes |

## Rollback Plan

Sin prod: `git revert` del commit. `V2`/`V3` son aditivas; `docker compose down -v`
resetea la DB local. Riesgo bajo.

## Dependencies

- `backend-foundation` completo (entidad `User`, `UserRepository`, schema). ✅
- Docker Postgres corriendo para los tests de integración.
- Dependencia nueva: `io.jsonwebtoken:jjwt` en `pom.xml`.
- **Tooling**: tras el monorepo, los tests corren desde `backend/` (`cd backend && ./mvnw test`); actualizar `test_command` del config openspec.

## Success Criteria

- [ ] `cd backend && ./mvnw test` pasa (unit + web slice + integración).
- [ ] `register` crea un CLIENTE con password hasheado (nunca plano en la DB).
- [ ] `login` válido devuelve JWT; inválido o **usuario inactivo** → 401.
- [ ] Endpoint protegido: 401 sin token, 200 con token válido; `/api/admin/**`: 403 sin rol ADMIN.
- [ ] Un ADMIN puede crear otro ADMIN, listar usuarios, cambiar rol y dar de baja lógica.
- [ ] Guard del último admin: no se puede degradar/desactivar al único ADMIN activo (422).
- [ ] Un usuario dado de baja deja de poder operar de inmediato (filtro lo rechaza).
- [ ] El ADMIN sembrado en `V3` puede loguearse. La entidad `User` nunca se expone (solo DTOs).
