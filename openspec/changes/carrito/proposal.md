# Proposal: Carrito (Shopping Cart)

## Intent

Dar al usuario autenticado un **carrito persistente** (1 por usuario) donde agregar, ver,
modificar y quitar productos antes del checkout. Es el puente entre `catalogo` (ya entregado)
y el futuro `checkout`: sin carrito no hay orden. El schema V1 ya define `cart` y `cart_item`,
asi que el cambio es **aditivo a nivel de codigo**, salvo una correccion de integridad
(**V4**: `UNIQUE(cart_id, product_id)`) que el schema original omitio. Reutiliza el borde de
seguridad de `auth` (todo bajo `anyRequest().authenticated()`) y los patrones de capas de
`catalogo`.

## Scope

### In Scope
- **5 endpoints** bajo `/api/cart` (ver API Contract), solo para usuarios autenticados
  (CLIENTE o ADMIN — sin restriccion de rol mas alla de estar logueado).
- **Creacion lazy del carrito**: se crea en el primer `POST /items`; no hay endpoint de creacion.
- **Merge de cantidades**: agregar un producto ya presente SUMA cantidad (upsert), no duplica fila ni rechaza.
- **Validacion de stock (check-only)**: `quantity <= product.stock` al agregar/actualizar; sin reserva ni decremento.
- **Aislamiento por dueño (anti-IDOR)**: el carrito SIEMPRE se carga desde el usuario del token, nunca desde un id de URL.
- **V4 migration**: `UNIQUE(cart_id, product_id)` en `cart_item` — cierra el gap de integridad detectado en exploracion.
- **Excepcion nueva**: `InsufficientStockException` → 422, mapeada en `GlobalExceptionHandler`.

### Out of Scope
- **Reserva/decremento de stock**: el stock solo se chequea; se decrementa en checkout. No se toca `products.stock` ni `stock_movement`.
- **Checkout / orders / order_items / pago**: feature posterior.
- **`@OneToMany` en `Cart`**: NO se agrega (decision de exploracion); los items se cargan por query explicita para evitar N+1 y lazy surprises.
- **Persistencia/congelado de precio en `cart_item`**: el precio se lee del producto al armar la respuesta; no se snapshot-ea.
- **Frontend** y cualquier dependencia nueva en `pom.xml`.

## Capabilities

> Criterio de `auth`/`catalogo`: un capability por recurso/responsabilidad. Nombres existentes
> verificados en `openspec/specs/`.

### New Capabilities
- `cart-management`: carrito persistente por usuario — creacion lazy, agregar/merge, actualizar, quitar, vaciar, validacion de stock check-only y aislamiento por dueño.

### Modified Capabilities
- `persistence-schema`: V4 agrega `UNIQUE(cart_id, product_id)` a `cart_item` (correccion de integridad sobre el schema V1).

## Approach

Construir **bottom-up** siguiendo los patrones de `catalogo`. Primero la **migracion V4**
(corrige el schema antes de tocar codigo). Luego **repos**: `CartRepository.findByUserEmail`
y `CartItemRepository` (`findByCartAndProduct`, `findByCart`, `deleteByCart`). **DTOs** como
records (`CartItemRequest` con Bean Validation, `CartItemResponse`, `CartResponse`).
`CartMapper` (`@Component`, mapeo manual). **Service = interfaz + impl** (`@Transactional` en
escrituras, `readOnly` en lecturas): resuelve `email → User`, carga/crea carrito, valida
producto activo y stock, hace merge, y setea `cart.updated_at` a mano. **Controller**
`/api/cart` con `@AuthenticationPrincipal UserDetails`. `InsufficientStockException` (422)
+ entrada en `GlobalExceptionHandler`. TDD RED→GREEN→REFACTOR en cada capa.

## API Contract

Todos requieren JWT (`401` sin token). Errores comunes: `404` producto/item inexistente o
inactivo, `422` stock insuficiente, `400` body invalido.

| Metodo | Ruta | Request body | Respuesta OK | Notas |
|--------|------|--------------|--------------|-------|
| GET | `/api/cart` | — | `200 CartResponse` | NO crea lazy; sin carrito devuelve uno vacio |
| POST | `/api/cart/items` | `{ productId, quantity>=1 }` | `200/201 CartResponse` | crea carrito si no existe; merge si el producto ya esta |
| PUT | `/api/cart/items/{id}` | `{ quantity>=1 }` | `200 CartResponse` | reemplaza cantidad; `quantity=0` → 400 |
| DELETE | `/api/cart/items/{id}` | — | `200 CartResponse` / `204` | quita un item del carrito del usuario |
| DELETE | `/api/cart` | — | `200 CartResponse` vacio / `204` | vacia todos los items |

**Shapes (records):**
- `CartItemRequest` = `{ productId: Long, quantity: int (>=1) }`
- `CartItemResponse` = `{ id, productId, sku, name, price, quantity, subtotal }`
- `CartResponse` = `{ cartId, items: CartItemResponse[], totalItems, totalPrice }`

## Business Rules

1. **Creacion lazy**: el carrito se crea en el primer `POST /items`; no hay POST de creacion explicito.
2. **Merge**: agregar un producto ya presente SUMA cantidades (upsert), no rechaza ni duplica.
3. **Stock check-only**: se valida contra `product.stock`; nunca se reserva ni decrementa.
4. **Stock insuficiente**: `quantity > stock` → `422 InsufficientStockException`.
5. **Producto inactivo**: agregar/actualizar producto `active=false` → `404 ResourceNotFoundException`.
6. **`quantity=0` en PUT**: rechazar con `400`; para quitar se usa DELETE.
7. **Anti-IDOR**: toda operacion carga el carrito desde el token; el `{id}` de item se valida que pertenezca a ese carrito (si no → 404).

## Build Order

1. `V4__add_cart_item_unique.sql` — `UNIQUE(cart_id, product_id)`.
2. Verificar entidades `Cart`/`CartItem` (ya existen, mapeos OK — **no** agregar `@OneToMany`).
3. Custom queries en `CartRepository` + `CartItemRepository`.
4. `InsufficientStockException` (422) + entrada en `GlobalExceptionHandler`.
5. DTOs (records) + `CartMapper`.
6. `CartService` interfaz + impl **con unit tests** (Mockito) — TDD.
7. `CartController` **con web tests** (`@WebMvcTest`, filtro JWT excluido).
8. `CartIntegrationTest` (Testcontainers Postgres, limpieza FK `cart_item → cart`).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `db/migration/V4__add_cart_item_unique.sql` | New | `UNIQUE(cart_id, product_id)` |
| `repository/CartRepository.java` | Mod | `findByUserEmail` |
| `repository/CartItemRepository.java` | Mod | `findByCartAndProduct`, `findByCart`, `deleteByCart` |
| `dto/request/CartItemRequest.java` | New | record + Bean Validation (`@Min(1)`) |
| `dto/response/{CartItemResponse,CartResponse}.java` | New | records de salida |
| `mapper/CartMapper.java` | New | Entity→DTO manual (`@Component`) |
| `service/CartService.java` (+ `impl/CartServiceImpl.java`) | New | logica de carrito |
| `controller/CartController.java` | New | `/api/cart`, autenticado |
| `exception/InsufficientStockException.java` + `GlobalExceptionHandler.java` | New/Mod | 422 |
| `model/{Cart,CartItem}.java` | None | ya existen, sin cambios |
| `config/SecurityConfig.java` | None | `anyRequest().authenticated()` ya cubre `/api/cart` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Falta `UNIQUE(cart_id, product_id)` → filas duplicadas | High | V4 lo agrega; service hace merge antes de insertar |
| Race en `POST /items` concurrente (doble insert) | Med | `UNIQUE` + catch `DataIntegrityViolationException` → reintento/merge |
| `cart.updated_at` no se actualiza solo (sin trigger/callback) | Med | Set manual en cada escritura del service |
| IDOR: item de otro usuario via `{id}` en URL | Med | Validar que el item pertenezca al carrito del token; si no → 404 |
| Principal es `UserDetails`, no `User` entity | Low | Service hace `findByEmail(principal.getUsername())` (1 hit por op, aceptable) |
| Cleanup IT en Testcontainer compartido | Med | Borrar en orden FK `cart_item → cart`; no borrar users seed |
| Stock baja a 0 con item ya en carrito | Low | El item permanece; el checkout (futuro) re-valida |

## Rollback Plan

Sin prod. `git revert` del commit revierte codigo + V4. La V4 es solo un `ADD CONSTRAINT`;
revertir = `DROP CONSTRAINT` (o `docker compose down -v` resetea la DB local). Bajo riesgo:
ninguna otra tabla se toca, ningun dato se borra.

## Dependencies

- `auth` (JWT, SecurityConfig, GlobalExceptionHandler, patrones DTO/mapper). OK
- `catalogo` (entidad `Product`, `ProductRepository`, campos `stock`/`active`). OK
- `backend-foundation` (entidades `Cart`/`CartItem`, repos stub, schema V1). OK
- Docker Postgres para IT. **Cero dependencias nuevas** en `pom.xml`.

## Success Criteria

- [ ] `cd backend && ./mvnw test` pasa (unit + web slice + integracion).
- [ ] `POST /items` crea carrito lazy y hace merge al repetir producto.
- [ ] `quantity > stock` → 422; producto inactivo → 404; `PUT quantity=0` → 400.
- [ ] Endpoints sin token → 401.
- [ ] Un usuario NO puede leer/mutar items de otro (anti-IDOR) → 404.
- [ ] `DELETE /api/cart` vacia todos los items; `DELETE /items/{id}` quita uno.
- [ ] V4 aplica y `UNIQUE(cart_id, product_id)` impide duplicados.
- [ ] La entidad nunca se expone: entran/salen solo DTOs.
