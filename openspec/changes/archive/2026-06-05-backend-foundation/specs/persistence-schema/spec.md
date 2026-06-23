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
