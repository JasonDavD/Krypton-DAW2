# Proposal: Backend Foundation

## Intent

Establecer el cimiento del backend de Krypton: un proyecto Spring Boot que arranca,
conecta a PostgreSQL y materializa el modelo de datos (8 tablas) vía Flyway. Sin esto,
ningún feature (auth, catálogo, carrito, checkout, reportes) puede construirse. Es la
base estructural sobre la que aplican las 4 reglas de arquitectura ya decididas.

## Scope

### In Scope
- Scaffolding Maven (base package `pe.com.krypton`): `pom.xml` (Web, Data JPA, PostgreSQL, Flyway, Validation, Security, Lombok), `KryptonApplication`, Maven wrapper.
- Las 8 entidades JPA + enums (`Role`, `OrderStatus`, `MovementType`).
- Migración Flyway `V1` con las 8 tablas, constraints (PK/FK/UNIQUE) y relaciones.
- Config base `application.yml`: perfil `local` → Docker Postgres `localhost:5432/krypton`, `ddl-auto: validate`, dialecto Postgres.
- Los 8 repositories (interfaces Spring Data).

### Out of Scope
- Lógica de negocio de features (auth/JWT, catálogo, carrito, checkout, reportes) → cambios posteriores.
- Controllers, services, DTOs, mappers de features.
- Deploy en nube y frontend.

## Capabilities

### New Capabilities
- `persistence-schema`: las 8 tablas con constraints, relaciones, enums y reglas del modelo (unique sku/email, FKs, kardex), creadas por Flyway V1 y validadas por las entidades JPA.
- `app-bootstrap`: el proyecto Spring Boot arranca, conecta a Postgres local, aplica migraciones al inicio y valida el mapeo entidad↔schema.

### Modified Capabilities
- None (greenfield).

## Approach

Spring Initializr (Maven). Por cada tabla: entidad JPA (siguiendo skill `krypton-backend`)
escrita JUNTO con su DDL en `V1`, para que `ddl-auto: validate` confirme la paridad.
Flyway dueño del schema; Hibernate solo valida. TDD: test de contexto + test de integración
con Testcontainers que verifica que `V1` aplica y crea las 8 tablas.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `pom.xml` | New | Dependencias + build |
| `src/main/java/pe/com/krypton/**` | New | App, model, enums, repository |
| `src/main/resources/application.yml` | New | Perfiles + datasource |
| `src/main/resources/db/migration/V1__*.sql` | New | Schema de 8 tablas |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Mismatch entidad JPA ↔ schema SQL (validate falla) | Med | Escribir entidad y DDL juntas; test Testcontainers en CI local |
| Tipos Postgres ↔ Java (decimal, timestamp, enum) | Low | Seguir skill `krypton-backend` |

## Rollback Plan

Greenfield, sin prod: revertir = `git revert` del commit (o borrar los archivos generados).
La DB local se resetea con `docker compose down -v`. Riesgo casi nulo.

## Dependencies

- Docker Postgres corriendo (`docker compose up -d`).
- JDK + Maven en la máquina.

## Success Criteria

- [ ] `mvn compile` y `mvn test` pasan (incluye test de integración Testcontainers).
- [ ] La app arranca con perfil `local` y Flyway aplica `V1` creando las 8 tablas.
- [ ] `ddl-auto: validate` pasa (entidades coinciden con el schema).
- [ ] Las 8 entidades + 3 enums + 8 repositories compilan.
