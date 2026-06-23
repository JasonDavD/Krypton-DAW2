# Krypton E-commerce — Modelo de Datos (Documento Didáctico)

**Proyecto:** Krypton — E-commerce B2C de artefactos tecnológicos
**Stack:** Spring Boot + MySQL · **Curso:** CIBERTEC EFSRT VI
**Propósito de este documento:** explicar el modelo de datos, las relaciones
entre tablas y las decisiones de diseño, de forma que TODO el equipo (codifica-
ción y documentación) entienda el *qué* y, sobre todo, el *porqué*.

---

## 1. Visión general

El modelo tiene **8 tablas**. Diagrama ER (fuente única): pegá
[`modelo.dbml`](./modelo.dbml) en [dbdiagram.io](https://dbdiagram.io) para verlo,
acomodarlo y exportarlo como imagen.

```
CATEGORY ─1:N─> PRODUCT ─1:N─> STOCK_MOVEMENT
                   │
            ┌──────┴──────┐
          1:N            1:N
            │              │
        CART_ITEM      ORDER_ITEM
            │              │
          N:1            N:1
            │              │
USER ─1:1─> CART       ORDER
  │                       ▲
  └──────────1:N──────────┘
```

| # | Tabla | Para qué existe |
| - | ----- | --------------- |
| 1 | `users` | Clientes y administradores que usan la plataforma |
| 2 | `categories` | Agrupa los productos (habilita reportes por categoría) |
| 3 | `products` | El catálogo: lo que se vende |
| 4 | `cart` | El carrito activo de cada usuario registrado |
| 5 | `cart_item` | Cada producto agregado al carrito |
| 6 | `orders` | Las compras confirmadas |
| 7 | `order_items` | El detalle (líneas) de cada compra |
| 8 | `stock_movement` | El historial de entradas/salidas de inventario (kardex) |

---

## 2. Las tablas en detalle

### `users`
| Campo | Tipo | Notas |
| ----- | ---- | ----- |
| `id` | bigint PK | identificador técnico |
| `name` | varchar | nombre del usuario |
| `email` | varchar **UNIQUE** | login, no se repite |
| `password` | varchar | **hasheada** (BCrypt), nunca en texto plano |
| `role` | varchar | `CLIENTE` o `ADMIN` |
| `created_at` | timestamp | fecha de registro |

### `categories`
| Campo | Tipo | Notas |
| ----- | ---- | ----- |
| `id` | bigint PK | |
| `name` | varchar **UNIQUE** | ej: "Laptops", "Celulares" |
| `description` | varchar | |

### `products`
| Campo | Tipo | Notas |
| ----- | ---- | ----- |
| `id` | bigint PK | clave técnica (surrogate) |
| `sku` | varchar **UNIQUE** | clave de negocio (ej: `LAP-DELL-15-001`) |
| `name` | varchar | |
| `description` | varchar | |
| `price` | decimal | precio actual |
| `stock` | int | **valor cacheado** del stock (ver §4) |
| `image_url` | varchar | |
| `active` | boolean | si se muestra o no en el catálogo |
| `category_id` | bigint FK → categories | |

### `cart`
| Campo | Tipo | Notas |
| ----- | ---- | ----- |
| `id` | bigint PK | |
| `user_id` | bigint FK → users, **UNIQUE** | 1 carrito activo por usuario |
| `created_at` / `updated_at` | timestamp | |

### `cart_item`
| Campo | Tipo | Notas |
| ----- | ---- | ----- |
| `id` | bigint PK | |
| `cart_id` | bigint FK → cart | |
| `product_id` | bigint FK → products | |
| `quantity` | int | **no guarda precio**: usa el precio VIVO del producto |

### `orders`
| Campo | Tipo | Notas |
| ----- | ---- | ----- |
| `id` | bigint PK | |
| `user_id` | bigint FK → users | quién compró |
| `order_date` | timestamp | |
| `status` | varchar | `PENDIENTE` / `CONFIRMADA` / `CANCELADA` |
| `total` | decimal | total de la compra |

### `order_items`
| Campo | Tipo | Notas |
| ----- | ---- | ----- |
| `id` | bigint PK | |
| `order_id` | bigint FK → orders | |
| `product_id` | bigint FK → products | |
| `quantity` | int | |
| `unit_price` | decimal | **SNAPSHOT**: precio congelado al comprar (ver §4) |

### `stock_movement` (el kardex)
| Campo | Tipo | Notas |
| ----- | ---- | ----- |
| `id` | bigint PK | |
| `product_id` | bigint FK → products | |
| `type` | varchar | `ENTRADA` o `SALIDA` |
| `quantity` | int | cantidad del movimiento |
| `reason` | varchar | compra, venta, ajuste, devolución |
| `reference` | varchar | referencia al origen (ej: `order_id`) |
| `created_at` | timestamp | |
| `created_by` | bigint FK → users | el admin que lo registró |

---

## 3. Las relaciones, una por una

1. **Category 1:N Product** — una categoría agrupa muchos productos; cada producto
   pertenece a una sola categoría. *Esto habilita los reportes por categoría.*
2. **User 1:N Order** — un cliente hace muchas órdenes; cada orden es de un cliente.
3. **User 1:1 Cart** — cada usuario tiene un único carrito activo (`user_id` es
   UNIQUE en `cart`).
4. **Cart 1:N CartItem** — un carrito tiene varias líneas, una por producto agregado.
5. **Order 1:N OrderItem** — una orden tiene una o más líneas de detalle.
6. **Product 1:N CartItem / OrderItem / StockMovement** — un producto aparece en
   muchos carritos, en muchas órdenes y tiene muchos movimientos de stock.

### ¿Por qué existen `cart_item` y `order_item`?

Entre **Order y Product** (y entre **Cart y Product**) hay una relación
**muchos-a-muchos**: una orden tiene muchos productos *y* un producto está en
muchas órdenes. En una base relacional un M:N **no se puede representar directo**:
se resuelve con una **tabla intermedia** (entidad asociativa). Esa tabla es
`order_item` (y `cart_item`). Además guardan datos propios de la relación
(`quantity`, `unit_price`), así que son obligatorias.

---

## 4. Conceptos clave (la parte importante)

### 4.1 Clave técnica vs. clave de negocio (`id` vs `sku`)

`products` tiene **dos** identificadores:

- **`id`** — *surrogate key*: autoincremental, interna, rápida. Es la que se usa
  en todas las FKs y relaciones.
- **`sku`** — *natural key* (clave de negocio): única, legible por humanos
  (`LAP-DELL-15-001`), la que ve y busca el admin.

> Regla: nunca uses el SKU como FK. Para conectar tablas, siempre el `id`.

### 4.2 El snapshot de precio (precio vivo vs. precio congelado)

Mirá la diferencia entre `cart_item` y `order_item`:

- **`cart_item` → precio VIVO.** No guarda precio. El carrito siempre muestra el
  precio ACTUAL del producto. Si mañana baja de oferta, el carrito lo refleja.
- **`order_item` → precio CONGELADO.** En el checkout, el precio del momento se
  **copia** a `unit_price`. La orden queda como una *foto histórica*.

**¿Por qué?** El precio de un producto cambia con el tiempo. Si la orden solo
referenciara al producto, al subir el precio mañana **todas las órdenes viejas
mostrarían el precio nuevo** — un error contable grave. Por eso se congela.

> El **checkout es la frontera** entre el precio vivo y el precio congelado.

### 4.3 El "valor cacheado" de `products.stock` (denormalización)

Con el kardex (`stock_movement`), el stock real **se puede calcular**:

```
stock real = SUM(ENTRADAS) − SUM(SALIDAS)
```

Ejemplo: entró 100, salió 3, salió 2 → stock real = 100 − 5 = **95**.

Entonces, en teoría, no haría falta la columna `stock`: el dato "verdadero" vive
en el historial de movimientos. **`stock_movement` es la fuente de la verdad.**

**¿Por qué guardamos igual el número en `products.stock`?** Por **performance**.
Con miles de productos y cientos de movimientos cada uno, calcular ese `SUM(...)`
en cada carga del catálogo sería lentísimo. Entonces guardamos el resultado ya
calculado: leerlo es instantáneo. Eso es el **valor cacheado** (técnicamente,
**denormalización**: guardar a propósito un dato redundante derivable, para ganar
velocidad de lectura).

> **Analogía — tu cuenta bancaria.** El banco podría calcular tu saldo sumando
> cada movimiento desde que abriste la cuenta. No lo hace: guarda el **saldo
> actual** (valor cacheado) y lo actualiza con cada transacción. El **historial**
> es la verdad; el **saldo** es la lectura rápida.
> En Krypton: `stock_movement` = historial · `products.stock` = saldo.

**El precio de esta jugada (la disciplina):** como el dato vive en dos lugares,
pueden desincronizarse. La regla que lo evita está en §4.4.

### 4.4 El checkout es UNA transacción atómica

Cuando un cliente confirma la compra, dentro de **una sola transacción** pasa todo
esto — o pasa **todo, o no pasa nada** (rollback):

1. Se crea la `order` y sus `order_items` (copiando el precio → snapshot).
2. Por cada ítem se genera una **SALIDA** en `stock_movement`.
3. Se descuenta `products.stock` (el valor cacheado).
4. Se vacía el `cart`.

Si cualquier paso falla (ej: no hay stock suficiente), se revierte TODO. Así
`stock_movement` y `products.stock` **siempre cuadran**, y nunca se vende algo sin
descontarlo. Esto cumple el requisito de **seguridad transaccional** de la rúbrica.

---

## 5. Glosario rápido

| Término | Qué es |
| ------- | ------ |
| **PK** (Primary Key) | identificador único de la fila |
| **FK** (Foreign Key) | columna que apunta a la PK de otra tabla |
| **UNIQUE** | valor que no se puede repetir en la tabla |
| **Surrogate key** | PK artificial, autoincremental (`id`) |
| **Natural / business key** | identificador con significado de negocio (`sku`) |
| **Entidad asociativa** | tabla intermedia que resuelve un M:N (`order_item`) |
| **Snapshot** | copia congelada de un valor en un momento dado (`unit_price`) |
| **Denormalización** | guardar un dato redundante a propósito, por performance |
| **Kardex** | registro histórico de entradas/salidas de inventario |
| **Transacción atómica** | conjunto de operaciones que pasan todas o ninguna |
