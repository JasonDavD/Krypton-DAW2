# persistence-schema Specification (DELTA)

Modificación a la capability existente `persistence-schema` para soportar la baja
lógica de usuarios.

## ADDED Requirements

### Requirement: Baja lógica de usuarios

`users` MUST tener una columna `active BOOLEAN NOT NULL DEFAULT true`, agregada por la
migración Flyway `V2`. Las filas existentes MUST quedar con `active = true`.

#### Scenario: Columna agregada por V2

- GIVEN una base con `V1` ya aplicada (tabla `users` sin `active`)
- WHEN Flyway aplica `V2`
- THEN `users` tiene la columna `active` NOT NULL con default `true`

#### Scenario: Filas previas activas

- GIVEN usuarios existentes antes de `V2`
- WHEN se aplica `V2`
- THEN todas esas filas quedan con `active = true`

### Requirement: Datos semilla del administrador

La migración Flyway `V3` MUST insertar al menos un usuario con rol ADMIN, `active = true`
y password BCrypt válido (no texto plano).

#### Scenario: Admin presente tras V3

- GIVEN `V2` aplicada
- WHEN Flyway aplica `V3`
- THEN existe una fila en `users` con `role = 'ADMIN'`, `active = true` y `password` con prefijo `$2`
