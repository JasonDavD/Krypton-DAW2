# Archive Report: auth (krypton-ecommerce) — CICLO SDD COMPLETO ✅

**Fecha**: 2026-06-05. **Verdict**: PASS (42/42 tests). **Rama**: `feat/auth`.

## Specs sincronizados a la fuente de verdad (openspec/specs/)
| Capability | Acción | Detalle |
|------------|--------|---------|
| `authentication` | Created (nueva) | 6 requisitos: registro, password hasheado, login JWT, validación stateless, borde de seguridad, seed admin |
| `user-management` | Created (nueva) | 7 requisitos: acceso ADMIN, listar, crear con rol, cambiar rol, baja/alta lógica, efecto inmediato, guard último admin |
| `persistence-schema` | Modified | +2 requisitos: baja lógica de usuarios (`users.active`, V2) y datos semilla del admin (V3) |

## Implementación
- **Seguridad**: Spring Security 6 stateless, JWT HS256 (jjwt 0.12.6), BCrypt. Filtro carga el usuario → baja inmediata.
- **Endpoints**: `POST /api/auth/{register,login}`; `GET/POST /api/admin/users`, `PATCH /api/admin/users/{id}/{role,status}`.
- **Migraciones**: `V2` (users.active), `V3` (seed admin `admin@krypton.pe`).
- **Reglas de negocio**: rol CLIENTE fijo en registro público; guard del último admin (422); soft delete (no borrado físico por FK de orders/cart).

## Tests: 42/42 verde
Unit (Mockito) 18 · Web slice (@WebMvcTest) 12 · Integración (Testcontainers) 12.

## Gotchas documentados (engram)
- [[krypton/jpa-default-vs-db-default]] — el DEFAULT de la DB no cubre inserts JPA.
- [[krypton/webmvctest-filter-gotcha]] — @WebMvcTest arrastra beans Filter.
- [[krypton/seed-shared-container-test-isolation]] — seed + contenedor compartido rompe asserts de tablas vacías.

## Findings pendientes (no bloqueantes, del verify)
- W1: agregar `springdoc` o quitar el `permitAll` de Swagger en `SecurityConfig`.
- S1-S5: test de reactivación, scenario filas previas, cleanup de IT, JaCoCo, rotar JWT_SECRET en prod.

## Estado del ciclo
proposal → spec → design → tasks → apply (7 fases) → verify (PASS) → **archive ✅**.
Próximo: push `feat/auth` + PR. Luego, siguiente feature (catálogo).
