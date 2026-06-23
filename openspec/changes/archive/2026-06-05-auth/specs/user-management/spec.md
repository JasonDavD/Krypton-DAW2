# user-management Specification

## Purpose

Define las operaciones de administración de usuarios (listar, crear con rol elegible,
cambiar rol, baja/alta lógica), restringidas a ADMIN, bajo `/api/admin/users`. Protege
contra quedarse sin administradores.

## Requirements

### Requirement: Acceso restringido a ADMIN

Todos los endpoints `/api/admin/**` MUST requerir un usuario autenticado con rol ADMIN.

#### Scenario: Cliente sin permiso

- GIVEN un token válido de un usuario CLIENTE
- WHEN accede a `GET /api/admin/users`
- THEN responde 403

#### Scenario: Admin autorizado

- GIVEN un token válido de un usuario ADMIN
- WHEN accede a `GET /api/admin/users`
- THEN responde 200

### Requirement: Listar usuarios

`GET /api/admin/users` MUST devolver los usuarios sin exponer el password.

#### Scenario: Listado sin password

- GIVEN existen varios usuarios
- WHEN un ADMIN hace `GET /api/admin/users`
- THEN responde 200 con id, name, email, role, active — nunca el password

### Requirement: Crear usuario con rol elegible

`POST /api/admin/users` MUST permitir crear un usuario con rol **CLIENTE o ADMIN**,
password BCrypt y email único. Es el único camino para crear un ADMIN.

#### Scenario: Admin crea otro admin

- GIVEN un ADMIN autenticado
- WHEN hace `POST /api/admin/users` con `role = ADMIN` y datos válidos
- THEN responde 201 y el nuevo usuario tiene rol ADMIN, activo, password hasheado

#### Scenario: Email duplicado

- GIVEN ya existe un usuario con ese email
- WHEN un ADMIN intenta crearlo de nuevo
- THEN responde 409

### Requirement: Cambiar rol

`PATCH /api/admin/users/{id}/role` MUST cambiar el rol del usuario indicado.

#### Scenario: Promover a admin

- GIVEN un usuario CLIENTE con id 5
- WHEN un ADMIN hace `PATCH /api/admin/users/5/role` con `role = ADMIN`
- THEN responde 200 y el usuario queda como ADMIN

### Requirement: Baja y alta lógica

`PATCH /api/admin/users/{id}/status` MUST activar o desactivar (`active`) al usuario.
El borrado físico MUST NOT existir (preserva el historial referenciado por orders/cart).

#### Scenario: Dar de baja

- GIVEN un usuario activo con id 7
- WHEN un ADMIN hace `PATCH /api/admin/users/7/status` con `active = false`
- THEN responde 200 y el usuario queda con `active = false`

#### Scenario: Reactivar

- GIVEN un usuario con `active = false`
- WHEN un ADMIN lo reactiva con `active = true`
- THEN responde 200 y el usuario vuelve a estar activo

### Requirement: Efecto inmediato de la baja

Un usuario dado de baja MUST perder el acceso de inmediato, aun con un JWT vigente.

#### Scenario: Token vigente pero usuario inactivo

- GIVEN un usuario con un JWT válido aún no vencido
- WHEN un ADMIN lo desactiva y el usuario hace otra request a un endpoint protegido
- THEN responde 401 (el filtro lo rechaza por inactivo)

### Requirement: Guard del último administrador

El sistema MUST impedir degradar o desactivar al último ADMIN activo (evitar lockout).

#### Scenario: Degradar al último admin

- GIVEN solo queda un ADMIN activo en el sistema
- WHEN se intenta `PATCH .../role` para quitarle el rol ADMIN
- THEN responde 422 y el cambio no se aplica

#### Scenario: Desactivar al último admin

- GIVEN solo queda un ADMIN activo
- WHEN se intenta `PATCH .../status` con `active = false` sobre él
- THEN responde 422 y el cambio no se aplica
