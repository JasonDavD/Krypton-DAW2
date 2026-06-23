# Verify Report: auth (krypton-ecommerce) — PASS ✅

**Modo**: Strict TDD. **Runner**: `cd backend && ./mvnw test`.
**Build & Tests**: BUILD SUCCESS, **42/42** (0 fail, 0 skip).

## Completeness — escenarios de spec vs cobertura

### authentication (6/6 requisitos cubiertos)
| Requisito | Cobertura |
|-----------|-----------|
| Registro de cliente (201/409/400) | `AuthServiceImplTest`, `AuthControllerTest`, `AuthSecurityIntegrationTest` ✅ |
| Password nunca en texto plano | `AuthServiceImplTest` (captura hash), `AuthControllerTest` (`$.password` ausente) ✅ |
| Login y emisión JWT (200/401/inactivo) | `AuthServiceImplTest`, `AuthControllerTest`, `JwtServiceTest` ✅ |
| Validación stateless (válido/expirado/alterado) | `JwtServiceTest` (5), `AuthSecurityIntegrationTest` ✅ |
| Borde de seguridad (público / protegido 401) | `AuthSecurityIntegrationTest` ✅ |
| ADMIN sembrado loguea | `AuthSecurityIntegrationTest` (seed V3) ✅ |

### user-management (7/7 requisitos cubiertos)
| Requisito | Cobertura |
|-----------|-----------|
| Acceso restringido ADMIN (403/200) | `AuthSecurityIntegrationTest` ✅ |
| Listar sin password | `AdminUserControllerTest`, `UserServiceImplTest` ✅ |
| Crear con rol elegible (201/409) | `UserServiceImplTest`, `AdminUserControllerTest` ✅ |
| Cambiar rol | `UserServiceImplTest`, `AdminUserControllerTest` ✅ |
| Baja y alta lógica | `UserServiceImplTest` (baja), `AdminUserControllerTest` ✅ |
| Efecto inmediato de la baja | `AuthSecurityIntegrationTest` (token vigente → 401) ✅ |
| Guard del último admin (degradar/desactivar → 422) | `UserServiceImplTest` (2), `AdminUserControllerTest` ✅ |

### persistence-schema delta (2/2 requisitos cubiertos)
| Requisito | Cobertura |
|-----------|-----------|
| `users.active` (V2, default true) | `SchemaIntegrationTest.v2_adds_active_column_to_users` ✅ |
| Seed ADMIN (V3) | `AuthSecurityIntegrationTest` + `KryptonApplicationTests` ✅ |

## Design adherence
HS256 vía jjwt ✅ · secreto por config/env ✅ · BCrypt ✅ · filtro carga usuario (baja inmediata) ✅ ·
`hasRole("ADMIN")` en `/api/admin/**` ✅ · guard en capa de servicio ✅ · soft delete ✅ ·
EntryPoint 401 / AccessDenied 403 JSON ✅. **Todas las decisiones de diseño implementadas.**

## Findings

### CRITICAL
- Ninguno.

### WARNING
- **W1**: `SecurityConfig` hace `permitAll` de `/swagger-ui/**` y `/v3/api-docs/**`, pero `springdoc-openapi` NO es dependencia → esos paths aún no existen. Inofensivo (permite rutas inexistentes), pero la spec menciona "Swagger público". **Acción**: agregar `springdoc` cuando se documente la API, o quitar ese `permitAll` por ahora.

### SUGGESTION
- **S1**: Sin test explícito de **reactivación** (`setStatus(id, true)`); el camino existe pero solo se testea la baja.
- **S2**: Scenario "filas previas activas" (default de V2 sobre filas pre-existentes) no testeado explícitamente; `DEFAULT TRUE` lo cubre.
- **S3**: `AuthSecurityIntegrationTest` commitea usuarios sin limpiar (contenedor compartido). Funciona por emails únicos; a futuro conviene `@Sql` cleanup / `@AfterEach`. Ver [[krypton/seed-shared-container-test-isolation]].
- **S4**: JaCoCo no configurado (diferido por decisión del proyecto; agregar si la rúbrica exige cobertura).
- **S5**: El default dev de `JWT_SECRET` está commiteado en `application.yml` (intencional y documentado solo-dev; prod inyecta por env). Recordar no usarlo en prod.

## Verdict: **PASS**
Sin findings bloqueantes. Los WARNING/SUGGESTION son no-bloqueantes y a futuro. El cambio cumple specs, design y tasks. Listo para `archive` (tras el commit a git).
