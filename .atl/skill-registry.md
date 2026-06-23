# Skill Registry — Krypton-Ecommerce

> Generado por sdd-init. Archivo de infraestructura (no es artefacto SDD).
> Última actualización: 2026-06-15

## Project Skills

| Skill | Trigger (cuándo se carga) | Archivo |
|-------|---------------------------|---------|
| `krypton-backend` | Escribir/revisar Java backend: entities, repositories, services, controllers, DTOs, mappers, migraciones Flyway | `.claude/skills/krypton-backend/SKILL.md` |
| `krypton-tdd` | Escribir tests o implementar features bajo Strict TDD (RED-GREEN-REFACTOR) | `.claude/skills/krypton-tdd/SKILL.md` |

## Frontend Skills

| Skill | Trigger (cuándo se carga) | Archivo |
|-------|---------------------------|---------|
| `frontend-design` (global) | Generar componentes Angular, páginas, UI — cualquier fase que produzca código de plantillas o estilos | `~/.claude/skills/frontend-design/SKILL.md` |

> No hay skill de proyecto para frontend todavia (TBD — se crea cuando el scaffold y el test runner esten decididos en la fase DESIGN).

## User Skills (escaneados)

Ninguno relevante para el stack Java/Spring (go-testing es de Go, no aplica).

## Compact Rules (inyectar en sub-agents que toquen código)

- **Commits**: conventional commits ÚNICAMENTE. NUNCA agregar `Co-Authored-By` ni atribución a IA.
- **Arquitectura** (skill `krypton-backend`): capas con interfaces, base package `pe.com.krypton`.
  `controller → service(+impl) → repository → model(@Entity)`. El controller NUNCA toca el
  repository. NUNCA exponer `@Entity` en la API: usar DTOs. Cada service es interfaz + impl.
- **Modelo de datos**: 8 tablas. Carrito persistido, SKU (natural key) + id (surrogate),
  kardex `stock_movement` + `products.stock` cacheado. Checkout en UNA transacción (`@Transactional`).
- **Migraciones**: Flyway desde `V1` (las 8 tablas). `ddl-auto: validate`. Nunca editar una
  migración ya aplicada — agregar `V{n+1}`.
- **Testing** (skill `krypton-tdd`): Strict TDD. `mvn test`. JUnit 5 + Mockito (unit, mock del
  repository interface), `@WebMvcTest` (web slice), Spring Boot Test + Testcontainers Postgres
  (integración, NO H2).
- **DB**: PostgreSQL 16, local vía Docker (`docker-compose.yml`), prod Supabase/Neon. Paridad dev/prod.

- **Frontend** (skill global `frontend-design`): Angular SPA con standalone components, greenfield.
  Aun no scaffoldeado — `ng new` ocurre en la fase APPLY. Test runner TBD (Karma/Jasmine vs Vitest).
  No aplicar reglas de testing frontend hasta que el scaffold y el test runner esten definidos.

## Reference docs
- `docs/arquitectura-backend.md` — estructura de paquetes + reglas de oro
- `docs/modelo-datos.md` — modelo de datos didáctico
- `docs/modelo.dbml` — fuente única del diagrama ER (dbdiagram.io)
