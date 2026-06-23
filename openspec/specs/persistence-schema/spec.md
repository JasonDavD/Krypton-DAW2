# persistence-schema Specification

## Purpose

Define el esquema relacional de Krypton (8 tablas) y sus reglas de integridad,
creado por la migración Flyway `V1`.

## Requirements

### Requirement: Tablas del modelo

Tras aplicar `V1`, el esquema MUST contener las 8 tablas: `users`, `categories`,
`products`, `cart`, `cart_item`, `orders`, `order_items`, `stock_movement`.

#### Scenario: Migración aplicada

- GIVEN una base de datos vacía
- WHEN Flyway aplica `V1`
- THEN existen las 8 tablas con sus columnas y PKs

### Requirement: Unicidad de claves de negocio

El esquema MUST imponer UNIQUE en `users.email`, `products.sku` y `categories.name`.

#### Scenario: SKU duplicado rechazado

- GIVEN un producto con sku `LAP-001`
- WHEN se inserta otro producto con sku `LAP-001`
- THEN la base rechaza la inserción por violación de UNIQUE

### Requirement: Integridad referencial

El esquema MUST definir FKs: `products.category_id`→`categories`; `cart.user_id`→`users`
(UNIQUE, 1:1); `cart_item`→(`cart`,`products`); `orders.user_id`→`users`;
`order_items`→(`orders`,`products`); `stock_movement`→(`products`, `users` vía `created_by`).

#### Scenario: FK inválida rechazada

- GIVEN no existe la categoría con id 999
- WHEN se inserta un product con `category_id` = 999
- THEN la base rechaza la inserción

#### Scenario: Un carrito por usuario

- GIVEN un usuario ya tiene un `cart`
- WHEN se inserta otro `cart` con el mismo `user_id`
- THEN la base lo rechaza (UNIQUE en `user_id`)

### Requirement: Enums como texto

Los enums MUST persistirse como STRING: `role` (CLIENTE/ADMIN), `orders.status`
(PENDIENTE/CONFIRMADA/CANCELADA), `stock_movement.type` (ENTRADA/SALIDA).

#### Scenario: Valor legible

- GIVEN una orden con status CONFIRMADA
- WHEN se persiste
- THEN la columna `status` contiene el texto `"CONFIRMADA"`

### Requirement: Snapshot de precio vs precio vivo

`order_items.unit_price` MUST ser columna propia NOT NULL (precio congelado).
`cart_item` MUST NOT tener columna de precio (usa el precio vivo del producto).

#### Scenario: La línea de orden conserva su precio

- GIVEN un order_item con `unit_price` = 100
- WHEN el precio del producto cambia a 120
- THEN el order_item sigue mostrando 100

### Requirement: Stock cacheado + kardex

`products.stock` MUST ser columna propia (default 0). `stock_movement` MUST registrar
movimientos con `type`, `quantity`, `reason`, `reference`.

#### Scenario: Movimiento registrado

- GIVEN un producto con stock 10
- WHEN se registra una SALIDA de 3
- THEN existe una fila en `stock_movement` con type=SALIDA y quantity=3

### Requirement: Inmutabilidad de migraciones

Una migración Flyway ya aplicada MUST NOT modificarse; todo cambio de esquema MUST ir
en una nueva `V{n+1}`.

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

### Requirement: Unique Constraint on cart_item (cart_id, product_id)

The `cart_item` table MUST have a UNIQUE constraint on `(cart_id, product_id)`.
This constraint MUST be introduced by Flyway migration `V4__add_cart_item_unique.sql`
as an additive change to the existing schema. The migration MUST NOT modify or
re-create any previously applied migration file.

#### Scenario: Duplicate cart item rejected at DB level

- GIVEN a `cart_item` row exists with `cart_id = 1` and `product_id = 5`
- WHEN another row is inserted with the same `(cart_id, product_id)` pair
- THEN the database rejects the insertion with a UNIQUE constraint violation

#### Scenario: V4 migration applied cleanly

- GIVEN a database with V1–V3 already applied
- WHEN Flyway applies `V4__add_cart_item_unique.sql`
- THEN the constraint `UNIQUE(cart_id, product_id)` exists on `cart_item`
- AND no existing rows are affected or removed

#### Scenario: Different cart, same product — allowed

- GIVEN `cart_item` rows exist for `(cart_id=1, product_id=5)` and `(cart_id=2, product_id=5)`
- WHEN no new insertion is attempted
- THEN both rows coexist without constraint violation

### Requirement: cart.updated_at Kept Current by Service Layer

The `cart.updated_at` column MUST be updated to the current timestamp on every
write operation (add item, update item, remove item, clear cart).
This MUST be managed explicitly by the service layer — no DB trigger or JPA
lifecycle callback is used for this purpose.

#### Scenario: updated_at advances after add

- GIVEN a cart with `updated_at = T0`
- WHEN an item is added via the service
- THEN `cart.updated_at` is a timestamp >= T0 (strictly after T0 in practice)

#### Scenario: updated_at advances after clear

- GIVEN a cart with `updated_at = T0`
- WHEN all items are cleared via the service
- THEN `cart.updated_at` is updated to a timestamp >= T0

#### Scenario: GET does not mutate updated_at

- GIVEN a cart with `updated_at = T0`
- WHEN `GET /api/cart` is called
- THEN `cart.updated_at` remains T0 (read-only operations do not touch it)
