# app-bootstrap Specification

## Purpose

Define el arranque de la aplicación Spring Boot y su cableado con PostgreSQL.

## Requirements

### Requirement: Arranque con perfil local

La app MUST arrancar con el perfil `local` conectando a Postgres en
`localhost:5432/krypton` (el contenedor de `docker-compose.yml`).

#### Scenario: El contexto carga

- GIVEN Postgres corriendo y el perfil `local` activo
- WHEN arranca la app
- THEN el contexto Spring carga sin errores

### Requirement: Migraciones al inicio

Flyway MUST aplicar las migraciones pendientes en el startup, antes de que la app
quede lista para servir.

#### Scenario: V1 aplicada al arrancar

- GIVEN una base sin migrar
- WHEN arranca la app
- THEN Flyway aplica `V1` y registra la versión en `flyway_schema_history`

### Requirement: Validación entidad↔schema

Con `ddl-auto: validate`, Hibernate MUST validar que las 8 entidades JPA coincidan con
el schema. Si no coinciden, el arranque MUST fallar.

#### Scenario: Mismatch detiene el arranque

- GIVEN una entidad con una columna que no existe en el schema
- WHEN arranca la app
- THEN el arranque falla con un error de validación de esquema

### Requirement: Verificación con Testcontainers

La integración con la base MUST verificarse con Testcontainers (`postgres:16`),
NO con H2, para mantener paridad con producción.

#### Scenario: Test de integración verde

- GIVEN un contenedor `postgres:16`
- WHEN corre el test de integración
- THEN `V1` se aplica y las 8 tablas existen
