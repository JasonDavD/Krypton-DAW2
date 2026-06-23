# authentication Specification

## Purpose

Define cómo el sistema identifica a los usuarios (registro y login), emite y valida
JWT de forma stateless, y dónde está el borde público/protegido. El password se guarda
hasheado; el ADMIN inicial nace por seed.

## Requirements

### Requirement: Registro de cliente

`POST /api/auth/register` MUST crear un usuario con rol **CLIENTE** (fijo, no
seleccionable por el cliente), password hasheado con BCrypt y email único.

#### Scenario: Registro exitoso

- GIVEN un email no registrado y datos válidos (name, email, password)
- WHEN se hace `POST /api/auth/register`
- THEN responde 201, el usuario queda con rol CLIENTE y `active = true`

#### Scenario: Email duplicado

- GIVEN ya existe un usuario con email `juan@mail.com`
- WHEN se registra otro con el mismo email
- THEN responde 409 y no se crea el usuario

#### Scenario: Input inválido

- GIVEN un body sin email o con password vacío
- WHEN se hace `POST /api/auth/register`
- THEN responde 400 con los errores de validación

### Requirement: Password nunca en texto plano

El password MUST persistirse hasheado con BCrypt; el sistema MUST NOT almacenar ni
devolver el password en claro.

#### Scenario: Hash en la base

- GIVEN un usuario recién registrado con password `Secret123`
- WHEN se inspecciona la fila en `users`
- THEN `password` es un hash BCrypt (prefijo `$2`), nunca `Secret123`

### Requirement: Login y emisión de JWT

`POST /api/auth/login` MUST validar credenciales y que el usuario esté **activo**, y
devolver un **access token JWT** (HS256) con `sub`=email, claim `role` y expiración 24h.

#### Scenario: Login exitoso

- GIVEN un usuario activo con credenciales correctas
- WHEN se hace `POST /api/auth/login`
- THEN responde 200 con un JWT válido (firma verificable, `role` y `exp` presentes)

#### Scenario: Credenciales inválidas

- GIVEN un email existente con password incorrecto
- WHEN se hace `POST /api/auth/login`
- THEN responde 401 y no emite token

#### Scenario: Usuario inactivo

- GIVEN un usuario con `active = false`
- WHEN intenta loguearse con credenciales correctas
- THEN responde 401 (no emite token)

### Requirement: Validación stateless del token

Cada request a un endpoint protegido MUST presentar `Authorization: Bearer <token>`.
El sistema MUST validar la firma y la expiración sin estado de sesión, y MUST cargar
el usuario para confirmar que sigue **activo**.

#### Scenario: Token válido

- GIVEN un JWT válido de un usuario activo
- WHEN accede a un endpoint protegido
- THEN la request procede con la identidad y rol del usuario

#### Scenario: Token expirado o alterado

- GIVEN un JWT con firma inválida o vencido
- WHEN accede a un endpoint protegido
- THEN responde 401

### Requirement: Borde de seguridad

`/api/auth/**` y la documentación (Swagger) MUST ser públicos. Todo otro endpoint
MUST requerir autenticación.

#### Scenario: Público sin token

- GIVEN no se envía token
- WHEN se hace `POST /api/auth/register` o `POST /api/auth/login`
- THEN la request es aceptada (no exige autenticación)

#### Scenario: Protegido sin token

- GIVEN un endpoint protegido (p.ej. `GET /api/admin/users`)
- WHEN se accede sin token
- THEN responde 401

### Requirement: ADMIN inicial sembrado

Tras las migraciones MUST existir al menos un usuario con rol ADMIN, activo, que
pueda loguearse.

#### Scenario: Admin sembrado loguea

- GIVEN la base con migraciones aplicadas (incluye el seed)
- WHEN el ADMIN sembrado hace login con sus credenciales
- THEN responde 200 con un JWT con `role = ADMIN`
