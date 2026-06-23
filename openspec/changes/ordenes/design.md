# Technical Design: Órdenes / Checkout con Pago Simulado

> Phase: DESIGN (the architectural HOW). Feeds `spec` + `tasks`. No implementation code — only
> decisions, signatures, annotations, and build order. Source of truth for intent/scope:
> `openspec/changes/ordenes/proposal.md` (engram `sdd/ordenes/proposal`).

## 1. Context & Architectural Approach

This change closes the purchase loop: turn the authenticated user's cart into a persisted order
through an **atomic checkout**, expose the order lifecycle (client history + admin management), and
simulate the payment that confirms it. The schema (V1: `orders`, `order_items`, `stock_movement`)
and the JPA entities/enums already exist — this is **100% additive at the code level, with NO new
migrations** (`ddl-auto: validate` stays green).

**Pattern**: classic layered Spring architecture, identical to `catalogo`/`carrito`:

```
controller  →  service (interface + impl)  →  repository  →  model (@Entity)
   DTOs            @Transactional core            JPA            never exposed
```

**Boundaries (non-negotiable, per project standards):**
- Controllers NEVER touch repositories. They depend only on `OrderService`.
- Entities (`Order`, `OrderItem`, `StockMovement`) NEVER cross the controller boundary. Only DTOs
  (records) enter/leave.
- Mapping is manual via a `@Component` `OrderMapper` (no MapStruct, mirrors `CartMapper`).
- One service interface (`OrderService`) + one impl (`OrderServiceImpl`).

**Build direction**: bottom-up (repos → DTOs/enums/exceptions → service → controllers →
integration), each behavioral layer driven test-first (Strict TDD). See §6.

**The architectural center of gravity is `OrderServiceImpl.checkout` — the single write
`@Transactional` that must be all-or-nothing.** Everything else (reads, pay, admin) is comparatively
simple. The design optimizes for that atomicity and for the stock race condition.

## 2. Component Map (layer by layer)

### 2.1 Repository layer

```java
// repository/ProductRepository.java  (MOD — add lock query, keep existing methods)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

```java
// repository/OrderRepository.java  (MOD)
List<Order> findByUserOrderByOrderDateDesc(User user);   // client history, newest first
Optional<Order> findByIdAndUser(Long id, User user);     // anti-IDOR fetch
// findAll(Pageable) inherited from JpaRepository — used by admin listing
```

```java
// repository/OrderItemRepository.java  (MOD)
List<OrderItem> findByOrder(Order order);                // lines of an order
```

```java
// repository/StockMovementRepository.java  (NONE)
// save() inherited from JpaRepository is enough — no derived queries added.
```

### 2.2 Enums & DTOs

```java
// model/enums/PaymentMethod.java  (NEW)
public enum PaymentMethod { CREDIT_CARD, YAPE, EFECTIVO }
```

```java
// dto/request/PaymentRequest.java  (NEW)
public record PaymentRequest(@NotNull PaymentMethod method) {}

// dto/request/OrderStatusUpdateRequest.java  (NEW)
public record OrderStatusUpdateRequest(@NotNull OrderStatus status) {}

// dto/response/OrderItemResponse.java  (NEW)
public record OrderItemResponse(
        Long id, Long productId, String productName,
        int quantity, BigDecimal unitPrice, BigDecimal subtotal) {}

// dto/response/OrderResponse.java  (NEW)
public record OrderResponse(
        Long id, Instant orderDate, String status,
        BigDecimal total, List<OrderItemResponse> items) {}
```

`PageResponse<OrderResponse>` is the **existing** generic wrapper (`dto/response/PageResponse.java`),
built via `PageResponse.of(Page<OrderResponse>)`.

### 2.3 Mapper

```java
// mapper/OrderMapper.java  (NEW, @Component, manual)
public OrderItemResponse toItemResponse(OrderItem item);     // subtotal = qty × item.unitPrice
public OrderResponse toResponse(Order order, List<OrderItem> items);
```
- `status` → `order.getStatus().name()` (String in the DTO).
- `total` → `order.getTotal()` (the persisted snapshot total — NOT recomputed from items).
- `unitPrice` → `item.getUnitPrice()` (the snapshot). **NEVER** `item.getProduct().getPrice()`.
- `productName` / `productId` → from `item.getProduct()` (LAZY; resolved inside the read tx).

### 2.4 Exceptions

```java
// exception/EmptyCartException.java          (NEW) extends RuntimeException  → 400
// exception/OrderStatusTransitionException.java (NEW) extends RuntimeException → 422
```
Both: single `(String message)` constructor, identical shape to `InsufficientStockException`.
Two new handlers in `GlobalExceptionHandler` returning `new ApiError(400|422, ex.getMessage())`.

### 2.5 Service

```java
// service/OrderService.java  (NEW)
OrderResponse checkout(String email);                          // @Transactional (write)
List<OrderResponse> getMyOrders(String email);                 // readOnly
OrderResponse getMyOrder(String email, Long orderId);          // readOnly, anti-IDOR
OrderResponse pay(String email, Long orderId, PaymentRequest request); // @Transactional
PageResponse<OrderResponse> getAllOrders(Pageable pageable);   // admin, readOnly
OrderResponse getOrder(Long orderId);                          // admin detail, readOnly
OrderResponse updateStatus(Long orderId, OrderStatus newStatus); // admin, @Transactional
```

```java
// service/impl/OrderServiceImpl.java  (NEW, @Service)
// Constructor deps: OrderRepository, OrderItemRepository, ProductRepository,
//                   StockMovementRepository, CartRepository, CartItemRepository,
//                   UserRepository, CartService, OrderMapper
```

### 2.6 Controllers

```java
// controller/OrderController.java  (NEW)  @RestController @RequestMapping("/api/orders")
POST   /checkout        201  checkout(principal)
GET    /               200  getMyOrders(principal)            → List<OrderResponse>
GET    /{id}           200  getMyOrder(principal, id)
POST   /{id}/pay       200  pay(principal, id, @Valid PaymentRequest)
```

```java
// controller/AdminOrderController.java  (NEW)  @RestController @RequestMapping("/api/admin/orders")
GET    /               200  getAllOrders(pageable)            → PageResponse<OrderResponse>
GET    /{id}           200  getOrder(id)
PUT    /{id}/status    200  updateStatus(id, @Valid OrderStatusUpdateRequest)
```
Client controller: `@AuthenticationPrincipal UserDetails principal` → `principal.getUsername()`
(mirrors `CartController`). Admin controller: NO `@AuthenticationPrincipal` (see ADR-6/ADR-9);
authorization is enforced by the existing `/api/admin/**` → `hasRole("ADMIN")` rule in
`SecurityConfig`. No `SecurityConfig` change (`/api/orders/**` falls under `anyRequest().authenticated()`).

## 3. Data Flow — Checkout (the critical path)

```
POST /api/orders/checkout (JWT)
  OrderController.checkout(principal)
    → OrderServiceImpl.checkout(email)        ╔═ @Transactional (single tx) ═════════════╗
        1. user  = resolveUser(email)              ResourceNotFound(404) if absent
        2. cart  = getOrThrowCart(user)            EmptyCartException(400) if no cart
        3. items = cartItemRepository.findByCart(cart)
                                                    EmptyCartException(400) if items empty
        4. for each cartItem:                       ── validation pass ──
             product = productRepository.findByIdWithLock(pid)   PESSIMISTIC_WRITE lock
                                                    ResourceNotFound(404) if absent
             if qty > product.stock → InsufficientStockException(422)  ⇒ ROLLBACK ALL
             accumulate total += qty × product.getPrice()
        5. save Order(user, orderDate=now, status=PENDIENTE, total)  → order.id
        6. for each (cartItem, lockedProduct):      ── mutation pass ──
             save OrderItem(order, product, qty, unitPrice = product.getPrice())
             product.setStock(stock - qty); productRepository.save(product)
             save StockMovement(product, SALIDA, qty,
                                reason="Venta orden #"+order.id,
                                reference="ORDER-"+order.id, createdBy=null, createdAt=now)
        7. cartService.clearCart(email)             PROPAGATION.REQUIRED ⇒ joins THIS tx
        8. return orderMapper.toResponse(order, createdOrderItems)
                                              ╚══════════════════════════════════════════╝
  ← 201 OrderResponse
```

**Two-pass design (validate-then-mutate)**: pass 4 locks + validates every line and computes the
total BEFORE any write. Only if ALL lines pass do we persist the order and mutate stock (passes
5–6). This guarantees no partial order can exist: a stock failure on line N throws before line N's
order is created, and the `@Transactional` rollback unwinds everything. The locked `Product`
references from pass 4 are reused in pass 6 (no second fetch, lock held until commit).

## 4. Integration Points

| Integration | Mechanism | Notes |
|-------------|-----------|-------|
| `CartService.clearCart(email)` | direct bean call inside checkout tx | `clearCart` is `@Transactional` (REQUIRED) → joins checkout tx; rolls back together. NO `@Lazy` needed: `OrderService → CartService` is acyclic (CartService does not depend on OrderService). |
| `ProductRepository.findByIdWithLock` | `@Lock(PESSIMISTIC_WRITE)` | `SELECT … FOR UPDATE` on Postgres; serializes concurrent checkouts on the same product row until commit. |
| `InsufficientStockException` (reused) | thrown in checkout | already mapped to 422 in `GlobalExceptionHandler`. |
| `ResourceNotFoundException` (reused) | user/product/order absent + anti-IDOR | already mapped to 404. Anti-IDOR returns 404 (not 403), matching `carrito`. |
| `SecurityConfig` | unchanged | `/api/admin/orders/**` already `hasRole(ADMIN)`; `/api/orders/**` already `authenticated()`. |
| `GlobalExceptionHandler` | +2 handlers | `EmptyCartException`→400, `OrderStatusTransitionException`→422. |

## 5. Architecture Decision Records

### ADR-1 — Checkout is a single write `@Transactional`, validate-then-mutate
**Decision**: `OrderServiceImpl.checkout(String email)` is the only write transaction. Order of
operations exactly as §3: resolveUser → getOrThrowCart → load items → (pass A) lock+validate every
product & accumulate total → save Order → (pass B) per item save OrderItem (price snapshot),
decrement stock, save StockMovement(SALIDA) → `cartService.clearCart(email)` → map & return.
**Rationale**: atomicity is the #1 risk. A single tx with validate-before-mutate makes a partial
order structurally impossible — any failure (empty cart 400, missing product 404, insufficient stock
422) throws and rolls back the whole unit, leaving zero rows in `orders`/`order_items`/`stock_movement`
and the cart untouched. Computing `total` in the validation pass avoids a second iteration.
**Rejected alternatives**:
- *Optimistic locking with `@Version`*: requires a schema/migration change (new column) — explicitly
  out of scope; also forces retry logic on the client. Rejected.
- *Application-level `synchronized` / in-memory lock*: doesn't survive multiple JVM instances and
  doesn't protect the DB row. Rejected.
- *Decrement-then-check (`UPDATE … SET stock = stock - q WHERE stock >= q`)*: avoids the lock but
  spreads business logic into SQL, complicates the per-item `StockMovement`/snapshot, and is harder
  to unit-test with Mockito. Pessimistic lock keeps logic in Java. Rejected for this feature size.

### ADR-2 — Pessimistic write lock on the checkout product fetch
**Decision**: add `findByIdWithLock(Long id)` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` to
`ProductRepository`; checkout uses it (and ONLY checkout). Cart operations keep using `findById`.
**Rationale**: the read→check→decrement sequence is a classic race; two concurrent checkouts could
both read stock=1 and both decrement to 0/-1 (oversell). `SELECT … FOR UPDATE` serializes them on
the row. No migration. Scoped to checkout to avoid lock contention on read-heavy catalog/cart paths.
**Rejected**: optimistic `@Version` (schema change, out of scope); locking inside `CartService`
(wrong layer — checkout owns the sale).

### ADR-3 — Minimal repository additions, anti-IDOR at the query
**Decision**: `OrderRepository.findByUserOrderByOrderDateDesc(User)` and `findByIdAndUser(Long,User)`;
`OrderItemRepository.findByOrder(Order)`; `StockMovementRepository` unchanged (inherited `save`).
**Rationale**: `findByIdAndUser` enforces ownership AT the query — if the order isn't the caller's,
the `Optional` is empty and the service throws `ResourceNotFoundException` (404), exactly the
`carrito` anti-IDOR pattern (no 403, no information leak about existence). Ordering history by
`orderDate` desc is the natural client view. Admin listing uses inherited `findAll(Pageable)`.
**Rejected**: a single `findById` + manual `order.getUser().getId().equals(...)` check in the service
(works but duplicates the ownership concern and leaves room to forget it). Query-level is safer.

### ADR-4 — Output DTOs as records; `OrderResponse` WITHOUT `userId`
**Decision**: `OrderResponse(id, orderDate, status:String, total, items)` and
`OrderItemResponse(id, productId, productName, quantity, unitPrice, subtotal)`. `status` is the enum
`.name()` String. `subtotal = quantity × unitPrice` computed in the mapper.
**Rationale**: records + manual mapper match the entire codebase (`CartResponse`, `ProductResponse`).
Exposing `status` as String keeps the wire contract decoupled from the Java enum. `subtotal` is
derived, never persisted.
**Open tension (FLAGGED)**: the proposal's API Contract lists `OrderResponse = { id, userId,
orderDate, status, total, items }` — it includes `userId`; this design's explicit ADR-4 shape omits
it. Resolution taken here: **follow the explicit design shape (no `userId`)**. For CLIENT endpoints
this is correct — every returned order is already the caller's own (anti-IDOR), so `userId` is
redundant. For the ADMIN listing, however, omitting `userId` means the admin cannot tell whose order
each row is — a real functional gap. **This MUST be reconciled in the `spec` phase** (see §7,
RISK-1). If admin user-attribution is required, the lowest-cost fix is to add `Long userId` to
`OrderResponse` (additive, harmless to clients) rather than introduce a separate `AdminOrderResponse`.
**Rejected**: separate `AdminOrderResponse` type now — premature; duplicates the record + a second
mapper method for one extra field.

### ADR-5 — Manual `@Component` `OrderMapper`, snapshot-faithful
**Decision**: `OrderMapper.toResponse(Order, List<OrderItem>)` builds `OrderResponse`;
`toItemResponse(OrderItem)` computes `subtotal = unitPrice × quantity`. `total` is read from
`order.getTotal()` (the persisted value), NOT recomputed. `unitPrice` is read from
`orderItem.getUnitPrice()`, **never** from `product.getPrice()`.
**Rationale**: mirrors `CartMapper` (items passed in explicitly — no `@OneToMany` on `Order`). The
snapshot is the whole point of `order_items.unit_price`: a later price change on the product must NOT
retroactively alter historical orders. Reading the live product price in the mapper would silently
break that invariant — hence the explicit rule.
**Rejected**: MapStruct (not used in this project; would add a dependency/processor — out of scope).
Recomputing `total` from items in the mapper (would mask any persisted-total drift and couples the
read path to the same arithmetic — keep total as the persisted source of truth).

### ADR-6 — Two controllers; client uses principal, admin relies on the security rule
**Decision**: `OrderController` (`/api/orders`, client) injects `@AuthenticationPrincipal UserDetails
principal` and forwards `principal.getUsername()` (the email) to the service. `AdminOrderController`
(`/api/admin/orders`) does NOT inject the principal; it forwards only the needed args
(`Pageable` / `orderId` / `OrderStatus`). Admin authorization is enforced entirely by the existing
`SecurityConfig` rule `/api/admin/** → hasRole("ADMIN")`.
**Rationale**: separating client vs admin into two controllers keeps URL prefixes, security, and
method shapes clean (same split as `ProductController` vs `AdminProductController`). The client
service methods need the email to scope ownership; the admin methods do not (no ownership check by
design — admin sees everything).
**Resolution of the ADR-6 vs ADR-9 tension**: the proposal/task prose mentioned admin service methods
"receive email too (for audit trail potential)", but the concrete signatures in ADR-8/ADR-9 omit it.
**This design drops email from admin service methods.** Justification: audit (`stock_movement.created_by`)
is explicitly out of scope and is `null` even in checkout; admin status changes write NO
`stock_movement` at all, so an admin email would be dead, unused data and needless coupling. If audit
is added later it is a purely additive change (add the param + `@AuthenticationPrincipal`).
**Rejected**: a single controller with mixed prefixes (muddies security boundaries); threading email
through admin methods now (YAGNI — no consumer).

### ADR-7 — Two new exceptions, mapped exactly like existing ones
**Decision**: `EmptyCartException` → 400, `OrderStatusTransitionException` → 422, each a
`RuntimeException` with a `(String message)` constructor, each added to `GlobalExceptionHandler` as
`ResponseEntity.status(...).body(new ApiError(code, ex.getMessage()))`.
**Rationale**: consistency with the existing handler family (`InsufficientStockException`,
`ResourceNotFoundException`, …) — same `ApiError(status, message)` body shape, same `@ExceptionHandler`
style. 400 = malformed request state (empty cart); 422 = semantically invalid transition (business
rule violation), matching `InsufficientStockException`/`LastAdminException` which also use 422.
**Rejected**: reusing `IllegalStateException`/`IllegalArgumentException` (no dedicated handler, would
fall through to a generic 500); one generic `OrderException` with a status field (less explicit, harder
to map per-case).

### ADR-8 — `pay()`: ownership + strict `PENDIENTE → CONFIRMADA` guard
**Decision**:
```java
@Transactional
public OrderResponse pay(String email, Long orderId, PaymentRequest request) {
    User user = resolveUser(email);
    Order order = orderRepository.findByIdAndUser(orderId, user)
        .orElseThrow(() -> new ResourceNotFoundException(...));        // IDOR → 404
    if (order.getStatus() != OrderStatus.PENDIENTE)
        throw new OrderStatusTransitionException(...);                 // 422
    order.setStatus(OrderStatus.CONFIRMADA);
    orderRepository.save(order);
    return orderMapper.toResponse(order, orderItemRepository.findByOrder(order));
}
```
**Rationale**: `findByIdAndUser` gives anti-IDOR for free (someone else's / nonexistent order → 404).
The guard enforces business rule #3 — only a PENDIENTE order can be paid; CONFIRMADA (double pay) or
CANCELADA → 422. `PaymentRequest.method` is captured/validated (`@NotNull`) but, by scope, NOT
persisted (no payment record, no external gateway — simulated). `@Transactional` (write) for the
status flip.
**Rejected**: allowing re-pay of CONFIRMADA as idempotent 200 (hides client bugs; rule says 422);
persisting the payment method (no column / out of scope).

### ADR-9 — `updateStatus()`: admin override, no transition validation
**Decision**:
```java
@Transactional
public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException(...));        // 404
    order.setStatus(newStatus);
    orderRepository.save(order);
    return orderMapper.toResponse(order, orderItemRepository.findByOrder(order));
}
```
**Rationale**: business rule #4 — the admin is the override authority and may move an order to ANY
status, so NO `OrderStatusTransitionException` guard here (that guard is client-only, in `pay`).
`findById` (not `findByIdAndUser`) because admin is not ownership-scoped. No email param (see ADR-6).
**Rejected**: reusing the transition guard for admin (contradicts the override rule); cascading side
effects on CANCELADA (e.g. restock) — explicitly out of scope (no `ENTRADA` reversal).

### ADR-10 — Test strategy: unit (Mockito) + web slice (@WebMvcTest) + integration (Testcontainers)
**Decision**: three test classes, Strict TDD throughout (`cd backend && ./mvnw test`).
- **`OrderServiceImplTest`** (unit, Mockito; all repos + `CartService` + `OrderMapper` mocked):
  checkout happy path (order/items/stock/movement/clearCart all invoked, correct args incl. price
  snapshot & total); empty-cart → `EmptyCartException`; product-missing → `ResourceNotFoundException`;
  stock-exceeded → `InsufficientStockException` and **no save of order/movement after the failing
  line** (verify mocks NOT called); `pay` happy (PENDIENTE→CONFIRMADA, save); `pay` wrong status →
  `OrderStatusTransitionException`; `pay`/`getMyOrder` IDOR (empty `findByIdAndUser` → 404);
  `updateStatus` sets arbitrary status with no guard.
- **`OrderControllerTest` + `AdminOrderControllerTest`** (`@WebMvcTest`, JWT filter excluded,
  `OrderService` mocked): one happy test per endpoint asserting status code + JSON shape; key error
  mappings (checkout empty→400, stock→422, pay wrong-status→422, IDOR→404, validation `@NotNull`
  method/status→400); `PaymentRequest`/`OrderStatusUpdateRequest` bind correctly.
- **`OrderIntegrationTest`** (Spring Boot Test + Testcontainers Postgres, **no H2**): full checkout
  flow (assert order+items rows, stock decremented, one `stock_movement` SALIDA per line, cart
  emptied); **rollback assertion** — force insufficient stock, expect 422 AND `orders` count
  unchanged (zero partial rows); pay flow; admin status update (arbitrary transition); anti-IDOR
  (client B cannot read client A's order → 404); 401 (no token) and 403 (client on `/api/admin/orders`).
**IT FK cleanup order** (teardown, children → parents):
`stock_movement → order_items → orders → cart_item → cart` (products/categories/users are seed data,
reused or cleaned last; never delete seeded users mid-suite).
**Rationale**: the unit layer pins business logic & atomic ordering cheaply with mocks; the web slice
pins HTTP contract + status mapping; the integration layer is the ONLY place that truly proves
atomicity/rollback and the pessimistic lock against a real Postgres — H2 cannot reproduce
`SELECT … FOR UPDATE` semantics faithfully, hence Testcontainers (project standard).
**Rejected**: H2 for integration (lock/transaction fidelity gap, project bans it); skipping the unit
layer and relying only on IT (slower feedback, weaker per-branch coverage of the checkout passes).

## 6. Build Order (Strict TDD: RED → GREEN → REFACTOR)

Declarative artifacts (records, enums, exception classes, repository method signatures) carry no
behavior, so they have no standalone RED; they are created as the GREEN scaffolding that lets the
first behavioral test compile. Behavioral code (service, controllers, full flow) is strictly
test-first.

**Phase 0 — Scaffolding (compile-time prerequisites, no logic):**
1. Repos: `ProductRepository.findByIdWithLock`; `OrderRepository.findByUserOrderByOrderDateDesc` +
   `findByIdAndUser`; `OrderItemRepository.findByOrder`.
2. Enums/DTOs: `PaymentMethod`; `PaymentRequest`, `OrderStatusUpdateRequest`, `OrderItemResponse`,
   `OrderResponse`.
3. Exceptions: `EmptyCartException`, `OrderStatusTransitionException` + 2 handlers in
   `GlobalExceptionHandler`.
4. `OrderMapper` (`@Component`).

**Phase 1 — Service (the core), test-first:**
- RED: write `OrderServiceImplTest` (all ADR-10 unit cases). It won't compile → introduce
  `OrderService` interface + empty `OrderServiceImpl` stubs (throw `UnsupportedOperationException`).
  Tests now compile and FAIL.
- GREEN: implement `checkout` (validate-then-mutate, §3), `getMyOrders`, `getMyOrder`, `pay`,
  `getAllOrders`, `getOrder`, `updateStatus` until all unit tests pass.
- REFACTOR: extract private helpers (`resolveUser`, `getOrThrowCart`, `buildOrder`, per-line
  `applyLine`), dedupe, keep tests green.

**Phase 2 — Controllers, test-first:**
- RED: `OrderControllerTest` + `AdminOrderControllerTest` (`@WebMvcTest`, mocked service) → create
  empty controllers → tests compile & FAIL.
- GREEN: wire endpoints/annotations (§2.6) until web tests pass.
- REFACTOR: tidy mappings/imports; tests stay green.

**Phase 3 — Integration, test-first:**
- RED: `OrderIntegrationTest` (Testcontainers) — full flow, rollback, pay, admin, IDOR, 401/403.
- GREEN: fix any wiring/tx/lock issues surfaced only against real Postgres until green.
- REFACTOR: stabilize fixtures + FK-ordered teardown; full `./mvnw test` green.

## 7. Risks & Unresolved Decisions (for spec/tasks)

| # | Risk / open decision | Severity | Disposition |
|---|----------------------|----------|-------------|
| RISK-1 | **`OrderResponse` has no `userId`** (ADR-4) but proposal contract lists it; admin listing then can't attribute orders to users. | **High (spec must reconcile)** | Recommend adding `Long userId` to `OrderResponse` (additive, client-safe) and mapping `order.getUser().getId()`. Deferred to `spec`. |
| RISK-2 | Admin `getAllOrders` maps each order's items via `findByOrder` per row → **N+1** queries. | Med | Acceptable at project scale. Future: `@EntityGraph`/batch fetch/single join. Document; don't optimize now. |
| RISK-3 | Atomicity bug if any checkout step escapes the tx. | High | Single `@Transactional`; IT rollback assertion (0 orders after 422) is the gate. |
| RISK-4 | Stock oversell under concurrency. | Med | `findByIdWithLock` (PESSIMISTIC_WRITE); only provable under Testcontainers. |
| RISK-5 | Snapshot violated by reading live product price at render time. | Med | ADR-5 hard rule: mapper reads `item.getUnitPrice()`; unit test asserts snapshot ≠ later product price. |
| RISK-6 | IT teardown FK-order violation on shared container. | Med | Fixed order: `stock_movement → order_items → orders → cart_item → cart`; never delete seeded users. |
| RISK-7 | `OrderStatus` Spanish values vs English docs. | Low | Keep `PENDIENTE/CONFIRMADA/CANCELADA` (VARCHAR, no check) — zero migration. |
| ASSUMPTION | `Order.orderDate` set to `Instant.now()` at checkout; `StockMovement.createdAt` = same `now`; `createdBy = null`. | — | Matches entities; confirm in spec. |
| ASSUMPTION | `cartService.clearCart` joins the checkout tx (REQUIRED) and `OrderService→CartService` is acyclic (no `@Lazy`). | — | Verified against `CartServiceImpl` deps. |
```
