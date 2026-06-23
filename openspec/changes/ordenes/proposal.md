# Proposal: Órdenes / Checkout con Pago Simulado

## Intent

Cerrar el flujo de compra: convertir el **carrito** del usuario autenticado en una **orden
persistente** mediante un checkout **atómico**, gestionar el **ciclo de vida** de esa orden
(historial del cliente + administración por el admin) y simular el **pago** que la confirma.
Es el eslabón que conecta `carrito` (ya entregado) con la venta real: sin checkout el carrito
no produce valor. El schema V1 ya define `orders`, `order_items` y `stock_movement`, y las
entidades/enums ya existen — el cambio es **100% aditivo a nivel de código, sin nuevas
migraciones**. Reutiliza el borde de seguridad de `auth`, el patrón anti-IDOR de `carrito` y
los patrones de capas de `catalogo`.

## Scope

### In Scope
- **3 capacidades funcionales** sobre `orders` / `order_items` / `stock_movement`:
  1. **Checkout atómico** — `POST /api/orders/checkout`: valida carrito, crea orden + items
     (snapshot de precio), decrementa stock con bloqueo pesimista, registra `stock_movement`
     (SALIDA) y vacía el carrito, todo en **una sola transacción** (rollback total ante fallo).
  2. **Ciclo de vida de la orden** — historial propio del cliente, detalle con anti-IDOR, y
     administración por el admin (listado paginado, detalle, cambio de estado).
  3. **Pago simulado** — `POST /api/orders/{id}/pay`: transición `PENDIENTE → CONFIRMADA` sin
     proveedor externo.
- **7 endpoints** (ver API Contract): 4 cliente bajo `/api/orders`, 3 admin bajo `/api/admin/orders`.
- **2 excepciones nuevas**: `EmptyCartException` (400) y `OrderStatusTransitionException` (422),
  ambas mapeadas en `GlobalExceptionHandler`. Reutiliza `InsufficientStockException` (422) y
  `ResourceNotFoundException` (404).
- **Bloqueo pesimista** (`@Lock(PESSIMISTIC_WRITE)`) en el fetch de producto del checkout — sin
  cambio de schema.

### Out of Scope
- **Pasarela de pago real** (Stripe/Culqi/MercadoPago/etc.) y validación de medios reales.
- **Facturación / boletas / comprobantes** y numeración fiscal.
- **Reembolsos, devoluciones y reversas de stock** (ningún `ENTRADA` por cancelación).
- **Reposición automática de stock** al cancelar una orden ya confirmada.
- **Notificaciones** (email/push) de orden o pago.
- **Frontend** y cualquier dependencia nueva en `pom.xml`.
- **Nuevas migraciones** — el schema V1 cubre todo (no se agrega `@Version` ni columnas).

## Capabilities

> Convención del proyecto: un capability por recurso/responsabilidad (como `cart-management`).
> Las tres capacidades funcionales del scope son tan acopladas (comparten `OrderResponse`,
> entidades y transacción) que se modelan como **un único spec** cohesivo.

### New Capabilities
- `order-management`: checkout atómico cart→orden con snapshot de precio, decremento de stock y
  `stock_movement`; historial e detalle del cliente con anti-IDOR; administración de órdenes por
  el admin (listado paginado, detalle, cambio de estado); y pago simulado que confirma la orden.

### Modified Capabilities
- None. El schema V1 (`orders`, `order_items`, `stock_movement`) se usa tal cual; `persistence-schema`
  no cambia (sin migración nueva).

## Approach

Construir **bottom-up** siguiendo los patrones de `catalogo`/`carrito`. El núcleo es
`OrderServiceImpl.checkout` como **único `@Transactional`**:

1. Resolver `email → User`, cargar el carrito y sus items; si está vacío → `EmptyCartException` (400).
2. Por cada item: cargar el `Product` con **bloqueo pesimista** (`@Lock(PESSIMISTIC_WRITE)` en
   `ProductRepository`), validar `quantity <= stock`; si no alcanza → `InsufficientStockException`
   (422) y **rollback total** (sin orden parcial).
3. Crear `Order` (status `PENDIENTE`, `total` = Σ `quantity × product.price`).
4. Crear un `OrderItem` por línea con **snapshot de precio** (`unit_price = product.getPrice()`).
5. Decrementar stock (`product.setStock(stock - qty); productRepository.save(product)`) e insertar
   un `StockMovement` SALIDA por item (`reference="ORDER-{orderId}"`, `created_by=null`).
6. Vaciar el carrito reutilizando **`CartService.clearCart(email)`** dentro de la misma transacción
   (PROPAGATION.REQUIRED → participa en la tx del checkout).

Lecturas (`getMyOrders`, `getMyOrder`, admin) en `@Transactional(readOnly=true)`. Anti-IDOR vía
`OrderRepository.findByUserAndId` (si no pertenece al usuario del token → 404). **Dos controllers**:
`OrderController` (`/api/orders`, cliente, `@AuthenticationPrincipal`) y `AdminOrderController`
(`/api/admin/orders`, ya cubierto por la regla `ROLE_ADMIN`). TDD RED→GREEN→REFACTOR por capa.

## API Contract

Todos requieren JWT (`401` sin token). `/api/admin/orders/**` exige `ROLE_ADMIN` (`403` si CLIENTE).

| Método | Ruta | Rol | Request body | Respuesta OK | Notas |
|--------|------|-----|--------------|--------------|-------|
| POST | `/api/orders/checkout` | CLIENTE | — | `201 OrderResponse` | atómico; carrito vacío→400, stock→422 |
| GET | `/api/orders` | CLIENTE | — | `200 OrderResponse[]` | solo órdenes propias |
| GET | `/api/orders/{id}` | CLIENTE | — | `200 OrderResponse` | anti-IDOR: ajena→404 |
| POST | `/api/orders/{id}/pay` | CLIENTE | `{ method }` | `200 OrderResponse` | PENDIENTE→CONFIRMADA; otro estado→422 |
| GET | `/api/admin/orders` | ADMIN | — (page,size,sort) | `200 PageResponse<OrderResponse>` | todas, paginado |
| GET | `/api/admin/orders/{id}` | ADMIN | — | `200 OrderResponse` | cualquier orden |
| PUT | `/api/admin/orders/{id}/status` | ADMIN | `{ status }` | `200 OrderResponse` | cualquier transición |

**Shapes (records):**
- `PaymentRequest` = `{ method: PaymentMethod }` (`CREDIT_CARD` | `YAPE` | `EFECTIVO`)
- `OrderStatusUpdateRequest` = `{ status: OrderStatus }` (`PENDIENTE` | `CONFIRMADA` | `CANCELADA`)
- `OrderItemResponse` = `{ id, productId, productName, quantity, unitPrice, subtotal }`
- `OrderResponse` = `{ id, userId, orderDate, status, total, items: OrderItemResponse[] }`
- `PageResponse<OrderResponse>` = wrapper genérico ya existente (content, page, size, totalElements, totalPages)

## Business Rules

1. **Carrito vacío en checkout** → `400 EmptyCartException`.
2. **Stock insuficiente en checkout** → `422 InsufficientStockException` + **rollback total** (sin orden parcial).
3. **Pagar orden CANCELADA o ya CONFIRMADA** → `422 OrderStatusTransitionException` (solo `PENDIENTE→CONFIRMADA`).
4. **Admin override**: el admin puede llevar la orden a cualquier estado vía `PUT /status` (sin guard de transición). El cliente NO puede cambiar estado.
5. **`stock_movement`**: un row **SALIDA por cada `OrderItem`**, `quantity=item.quantity`, `reason="Venta orden #{orderId}"`, `reference="ORDER-{orderId}"`, `created_by=null` (automatizado).
6. **Snapshot de precio**: `order_items.unit_price = product.getPrice()` en el momento del checkout (no se relee después).
7. **Total de la orden**: Σ `(quantity × product.price)` sobre los items del carrito.
8. **Bloqueo pesimista**: el fetch de `Product` en checkout usa `@Lock(PESSIMISTIC_WRITE)` para evitar la race read→check→decrement. Sin migración.
9. **Pago simulado**: `POST /{id}/pay` con `{ method }` ∈ {CREDIT_CARD, YAPE, EFECTIVO}; transiciona `PENDIENTE→CONFIRMADA`; sin llamada externa.
10. **`clearCart` reutilizado**: `CartService.clearCart(email)` se invoca al final del checkout `@Transactional` y participa en la **misma** transacción.

## Build Order

1. Repos: `OrderRepository` (`findByUser`, `findByUserAndId`, `findAll(Pageable)` heredado), `OrderItemRepository` (`findByOrder`), `ProductRepository` (método `@Lock(PESSIMISTIC_WRITE)` para checkout).
2. Enums/DTOs: `PaymentMethod`, `PaymentRequest`, `OrderStatusUpdateRequest`, `OrderItemResponse`, `OrderResponse` + `OrderMapper` (`@Component`, manual).
3. Excepciones: `EmptyCartException` (400) y `OrderStatusTransitionException` (422) + entradas en `GlobalExceptionHandler`.
4. `OrderService` (interfaz) + `OrderServiceImpl` **con unit tests** (Mockito) — checkout atómico, pay, IDOR, admin status. TDD.
5. `OrderController` + `AdminOrderController` **con web tests** (`@WebMvcTest`, filtro JWT excluido).
6. `OrderIntegrationTest` (Testcontainers Postgres): checkout feliz, carrito vacío, stock insuficiente (rollback), pago, anti-IDOR, listado admin.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `repository/OrderRepository.java` | Mod | `findByUser`, `findByUserAndId` (anti-IDOR) |
| `repository/OrderItemRepository.java` | Mod | `findByOrder` |
| `repository/ProductRepository.java` | Mod | método con `@Lock(PESSIMISTIC_WRITE)` para checkout |
| `repository/StockMovementRepository.java` | None | solo `save()` heredado |
| `model/enums/PaymentMethod.java` | New | CREDIT_CARD, YAPE, EFECTIVO |
| `dto/request/{PaymentRequest,OrderStatusUpdateRequest}.java` | New | records + Bean Validation |
| `dto/response/{OrderItemResponse,OrderResponse}.java` | New | records de salida |
| `mapper/OrderMapper.java` | New | Entity→DTO manual (`@Component`) |
| `service/OrderService.java` (+ `impl/OrderServiceImpl.java`) | New | checkout, getMyOrders, getMyOrder, pay, admin |
| `controller/OrderController.java` | New | `/api/orders`, cliente |
| `controller/AdminOrderController.java` | New | `/api/admin/orders`, admin |
| `exception/{EmptyCartException,OrderStatusTransitionException}.java` + `GlobalExceptionHandler.java` | New/Mod | 400 + 422 |
| `model/{Order,OrderItem,StockMovement}.java`, enums `OrderStatus`/`MovementType` | None | ya existen, sin cambios |
| `config/SecurityConfig.java` | None | `/api/orders/**` → authenticated; `/api/admin/**` → ROLE_ADMIN (ya cubiertos) |
| `db/migration/` | None | **sin migración nueva** — schema V1 completo |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Checkout no atómico → orden/stock/cart inconsistentes ante fallo | High | Un único `@Transactional`; rollback total; IT que valida 0 órdenes tras fallo de stock |
| Race read→check→decrement de stock concurrente | Med | `@Lock(PESSIMISTIC_WRITE)` en el fetch de producto del checkout |
| IDOR: cliente accede a orden ajena vía `{id}` | Med | `findByUserAndId(user, id)`; si vacío → 404 (no 403), patrón de `carrito` |
| Pagar/transicionar orden en estado inválido (cliente) | Med | Guard `PENDIENTE→CONFIRMADA`; otro estado → 422 `OrderStatusTransitionException` |
| `clearCart` doble `resolveUser` dentro de la tx | Low | Aceptable (REQUIRED, misma tx); coste 1 query extra, no es bug |
| Cleanup IT en Testcontainer compartido (orden FK) | Med | Borrar `order_items → stock_movement → orders → cart_item → cart`; no borrar users seed |
| Valores de `OrderStatus` en español vs docs en inglés | Low | Se mantienen `PENDIENTE/CONFIRMADA/CANCELADA` (ya en VARCHAR, sin check); cero migración |
| Snapshot de precio omitido por leer precio actual al renderizar | Med | Persistir `unit_price` en `order_items` al crear; el mapper lee del item, no del producto |

## Rollback Plan

Sin prod. `git revert` del commit revierte todo el código (cambio puramente aditivo: clases nuevas
+ métodos de repo + 2 excepciones). **No hay migración**, así que no hay nada que deshacer en DB;
`docker compose down -v` resetea la base local. Riesgo bajo: ninguna tabla cambia de forma, ningún
dato existente se borra ni se altera fuera del flujo transaccional del propio checkout.

## Dependencies

- `auth` (JWT, SecurityConfig, GlobalExceptionHandler, patrones DTO/mapper). OK
- `catalogo` (entidad `Product`, `ProductRepository`, campo `stock`/`price`). OK
- `carrito` (`Cart`/`CartItem`, `CartService.clearCart`, `InsufficientStockException`, anti-IDOR). OK
- `backend-foundation` (entidades `Order`/`OrderItem`/`StockMovement`, enums, repos stub, schema V1). OK
- Docker Postgres para IT. **Cero dependencias nuevas** en `pom.xml`.

## Success Criteria

- [ ] `cd backend && ./mvnw test` pasa (unit + web slice + integración Testcontainers).
- [ ] `POST /checkout` crea orden + items (snapshot de precio), decrementa stock, inserta un `stock_movement` SALIDA por item y vacía el carrito — todo atómico.
- [ ] Carrito vacío → 400; stock insuficiente → 422 con **0 órdenes creadas** (rollback verificado en IT).
- [ ] `GET /api/orders` devuelve solo órdenes propias; `GET /api/orders/{id}` de otra → 404 (anti-IDOR).
- [ ] `POST /{id}/pay` confirma una orden PENDIENTE; sobre CANCELADA/CONFIRMADA → 422.
- [ ] `GET /api/admin/orders` pagina todas las órdenes; `PUT /{id}/status` permite cualquier transición (admin).
- [ ] Endpoints sin token → 401; cliente sobre `/api/admin/orders/**` → 403.
- [ ] La entidad nunca se expone: entran/salen solo DTOs.
