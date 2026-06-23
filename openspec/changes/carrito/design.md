# Design: Carrito (Shopping Cart)

> Technical design for the `cart-management` capability. Architecture-level HOW.
> Reads from `proposal.md`. Does NOT contain implementation code — only decisions,
> class names, signatures, annotations, and the build order. Tasks are derived later.

## 1. Architecture Overview

Standard layered flow already established by `auth` and `catalogo`:

```
CartController  (HTTP, /api/cart, @AuthenticationPrincipal → email)
      │  email + DTO in, DTO out
      ▼
CartService (interface)  ──►  CartServiceImpl (@Service, @Transactional)
      │  resolves email→User, owns business rules, sets updated_at, maps via CartMapper
      ▼
CartRepository / CartItemRepository / ProductRepository / UserRepository  (Spring Data JPA)
      ▼
PostgreSQL  (V4 adds UNIQUE(cart_id, product_id))
```

Hard rules carried from project standards:
- Controller NEVER touches a repository. Only the service does.
- The `@Entity` (`Cart`, `CartItem`, `Product`, `User`) NEVER crosses the controller boundary — only records (`CartItemRequest`, `UpdateQuantityRequest`, `CartItemResponse`, `CartResponse`).
- Service = interface (`CartService`) + impl (`CartServiceImpl`).
- `ddl-auto: validate` — schema changes happen ONLY through Flyway (V4).
- Entities `Cart` / `CartItem` are NOT modified (proposal decision: no `@OneToMany`, no `@PreUpdate`).

### Principal resolution (verified)
`JwtAuthenticationFilter` authenticates with a Spring `org.springframework.security.core.userdetails.User` (NOT the domain `User`). Therefore `@AuthenticationPrincipal` yields a `UserDetails` whose `getUsername()` is the **email**. The controller passes the email string down; `CartServiceImpl` resolves `email → domain User` via `UserRepository.findByEmail(...)` (one hit per op — accepted in proposal Risks).

---

## 2. Layer-by-Layer Design

### 2.1 Migration — `V4__add_cart_item_unique.sql`

```sql
ALTER TABLE cart_item
    ADD CONSTRAINT uq_cart_item_cart_product UNIQUE (cart_id, product_id);
```

- Closes the integrity gap left by V1 (cart_item had no composite uniqueness).
- **No entity annotation needed**: Hibernate `validate` checks tables/columns, NOT unique constraints — so `CartItem` stays untouched and there is no schema/entity drift risk. The constraint is enforced by Postgres and exercised by the integration test.
- Idempotent intent: applied once as V4; never edited after (V1–V3 already applied).

### 2.2 Entities — NO CHANGES

`Cart` and `CartItem` already map the V1 schema correctly:
- `Cart`: `id`, `@OneToOne(LAZY) User user` (`user_id UNIQUE`), `createdAt`, `updatedAt`. No `@OneToMany items` — items are loaded by explicit query (avoids N+1 / LazyInitialization surprises).
- `CartItem`: `id`, `@ManyToOne(LAZY) Cart cart`, `@ManyToOne(LAZY) Product product`, `int quantity`.

### 2.3 Repositories (custom methods)

`CartRepository extends JpaRepository<Cart, Long>`
```java
Optional<Cart> findByUser(User user);
```

`CartItemRepository extends JpaRepository<CartItem, Long>`
```java
Optional<CartItem> findByCartAndProduct(Cart cart, Product product);
List<CartItem>     findByCart(Cart cart);
void               deleteByCart(Cart cart);   // derived bulk delete; runs inside service @Transactional
```

Notes:
- `findByUser(User)` (not `findByUserEmail`) — the service resolves the `User` entity once at the top of each write op and reuses it. The entity is REQUIRED anyway to set `cart.setUser(user)` on lazy creation, so resolving it first is the natural design. This supersedes the proposal's tentative `findByUserEmail`.
- `deleteByCart` is a Spring Data derived delete — no `@Query`/`@Modifying` needed; the service's `@Transactional` provides the write context.

### 2.4 DTOs (records, package `dto.request` / `dto.response`)

**Requests**
```java
// POST /api/cart/items  — Integer (not int) so a missing field fails @NotNull → 400, and 0 fails @Min(1) → 400
public record CartItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity) {}

// PUT /api/cart/items/{itemId}  — quantity replacement; 0 → 400 (use DELETE to remove)
public record UpdateQuantityRequest(
        @NotNull @Min(1) Integer quantity) {}
```

**Responses**
```java
public record CartItemResponse(
        Long itemId,
        Long productId,
        String productName,
        String sku,
        BigDecimal price,       // read live from Product (NOT snapshotted)
        int quantity,
        BigDecimal subtotal) {} // price × quantity, computed in mapper

public record CartResponse(
        Long cartId,            // null when the user has no cart yet (empty-cart GET)
        List<CartItemResponse> items,
        BigDecimal total,       // Σ subtotal, computed in mapper
        Instant updatedAt) {}   // null for the synthesized empty cart
```

This is the canonical response shape (supersedes the proposal's `id`/`name`/`totalItems`/`totalPrice` field names). `subtotal` and `total` are derived in the mapper; the DB stores neither.

### 2.5 Mapper — `CartMapper` (`@Component`, manual mapping)

```java
CartItemResponse toItemResponse(CartItem item);          // subtotal = price × quantity
CartResponse     toResponse(Cart cart, List<CartItem> items); // total = Σ subtotal
CartResponse     emptyCart();                            // cartId=null, items=[], total=ZERO, updatedAt=null
```

- `toResponse` takes `items` **as a parameter** (not from a `Cart.getItems()` association) because `Cart` has no `@OneToMany`. The service loads them via `cartItemRepository.findByCart(cart)` and passes them in. This is the deliberate anti-N+1 decision.
- `subtotal = price.multiply(BigDecimal.valueOf(quantity))`; `total` = reduce over item subtotals starting at `BigDecimal.ZERO`.
- `price`/`productName`/`sku` are read from `item.getProduct()`.

### 2.6 Exception — `InsufficientStockException` (+ handler)

```java
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) { super(message); }
}
```

`GlobalExceptionHandler` — add one handler, mirroring the **existing 422 handler** (`LastAdminException`) for the status and the `CategoryInUseException` one-liner for the shape:
```java
@ExceptionHandler(InsufficientStockException.class)
public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiError(422, ex.getMessage()));
}
```
(Clarification: the task said "mirror CategoryInUseException" for the *code pattern*; the *status* is 422 per business rule, identical to the already-present `LastAdminException` handler.)

Existing handlers already cover the rest: product/item not found / inactive → `ResourceNotFoundException` (404); invalid body → `MethodArgumentNotValidException` (400). 401 comes from the security entry point.

### 2.7 Service — `CartService` interface

```java
public interface CartService {
    CartResponse getCart(String email);
    CartResponse addItem(String email, CartItemRequest request);
    CartResponse updateItem(String email, Long itemId, UpdateQuantityRequest request);
    void         removeItem(String email, Long itemId);   // 204 — no body
    void         clearCart(String email);                 // 204 — no body
}
```

Return shapes are driven by the mutation-response decision (§3, ADR-2): GET/POST/PUT return the full `CartResponse`; the two DELETEs return `void`.

### 2.8 Service — `CartServiceImpl` (`@Service`)

Dependencies (constructor injection): `CartRepository`, `CartItemRepository`, `ProductRepository`, `UserRepository`, `CartMapper`, and a `@Lazy CartService self` (self-proxy — see ADR-1).

**Private helpers**
```java
private User    resolveUser(String email);          // findByEmail → ResourceNotFoundException(404) if absent
private Cart    getOrCreateCart(User user);         // findByUser; if absent build+saveAndFlush new Cart (createdAt/updatedAt=now) — WRITE PATHS ONLY
private Product resolveActiveProduct(Long id);      // findById + active check → ResourceNotFoundException(404) if missing/inactive
private void    validateStock(Product p, int qty);  // qty > p.getStock() → InsufficientStockException(422)
private void    touch(Cart cart);                   // cart.setUpdatedAt(Instant.now()); cartRepository.save(cart)
private CartItem requireOwnedItem(CartItem item, User user); // IDOR guard, see ADR-3
private CartResponse currentCart(Cart cart);        // cartMapper.toResponse(cart, cartItemRepository.findByCart(cart))
```

**`getCart`** — `@Transactional(readOnly = true)`
- Resolve user. `cartRepository.findByUser(user)`:
  - present → `currentCart(cart)`.
  - absent → `cartMapper.emptyCart()` — **does NOT persist** (proposal API rule: "GET NO crea lazy"). This intentionally narrows task point #4: `getOrCreateCart` is used by WRITE paths only; GET synthesizes an empty cart.

**`addItem`** — see ADR-1 for the full concurrency design. Thin orchestrator (NOT `@Transactional`):
```
try   { return self.attemptAddItem(email, request); }      // tx1 (proxied)
catch (DataIntegrityViolationException ex) {
      return self.mergeOnConflict(email, request); }        // tx2 (proxied)
```

**`attemptAddItem(email, request)`** — `@Transactional` (public, proxied):
1. `user = resolveUser(email)`
2. `cart = getOrCreateCart(user)`  *(saveAndFlush surfaces a UNIQUE(user_id) race here too)*
3. `product = resolveActiveProduct(request.productId())`
4. `existing = cartItemRepository.findByCartAndProduct(cart, product)`
5. `finalQty = existing.map(i -> i.getQuantity() + request.quantity()).orElse(request.quantity())`
6. `validateStock(product, finalQty)`
7. if absent → build `CartItem` (cart, product, quantity=request.quantity()) and `cartItemRepository.saveAndFlush(item)` — **the constraint-violation catch point**.
   if present → `existing.setQuantity(finalQty); cartItemRepository.save(existing)` (merge — sequential happy path, never hits the constraint).
8. `touch(cart)`
9. return `currentCart(cart)`
- Lets `DataIntegrityViolationException` PROPAGATE (does not catch) → tx1 rolls back cleanly.

**`mergeOnConflict(email, request)`** — `@Transactional` (public, proxied; runs as an independent tx because the caller `addItem` is non-transactional):
- Same as `attemptAddItem` but the row now exists → goes through the merge branch (re-fetch `findByCartAndProduct`, sum quantity, `validateStock(finalQty)`, `save`, `touch`, return). Defensive fallback to insert if still absent.

**`updateItem`** — `@Transactional`:
1. `user = resolveUser(email)`
2. `item = cartItemRepository.findById(itemId).orElseThrow(ResourceNotFoundException)` (404)
3. `requireOwnedItem(item, user)` — IDOR guard (ADR-3) → 404 if not owner
4. `product = resolveActiveProduct(item.getProduct().getId())` (inactive product → 404, per BR5)
5. `validateStock(product, request.quantity())`
6. `item.setQuantity(request.quantity()); cartItemRepository.save(item)`
7. `touch(item.getCart())`; return `currentCart(item.getCart())`

**`removeItem`** — `@Transactional`, returns `void`:
1. resolve user; `item = findById(itemId)` → 404 if absent
2. `requireOwnedItem(item, user)` → 404 if not owner
3. `cartItemRepository.delete(item)`; `touch(item.getCart())`

**`clearCart`** — `@Transactional`, returns `void`:
1. resolve user; `cartRepository.findByUser(user)`:
   - present → `cartItemRepository.deleteByCart(cart); touch(cart)`
   - absent → no-op (controller still returns 204)

### 2.9 Controller — `CartController` (`@RestController`, `@RequestMapping("/api/cart")`)

Constructor-injects `CartService`. Current user via `@AuthenticationPrincipal UserDetails principal` → `principal.getUsername()`.

| Method | Mapping | Body | Code | Returns |
|--------|---------|------|------|---------|
| `getCart` | `@GetMapping` | — | 200 | `CartResponse` |
| `addItem` | `@PostMapping("/items")` + `@ResponseStatus(CREATED)` | `@Valid CartItemRequest` | 201 | `CartResponse` |
| `updateItem` | `@PutMapping("/items/{itemId}")` | `@Valid UpdateQuantityRequest` | 200 | `CartResponse` |
| `removeItem` | `@DeleteMapping("/items/{itemId}")` + `@ResponseStatus(NO_CONTENT)` | — | 204 | `void` |
| `clearCart` | `@DeleteMapping` + `@ResponseStatus(NO_CONTENT)` | — | 204 | `void` |

Controller is a thin pass-through: extract email, delegate, return. No business logic.

### 2.10 SecurityConfig — NO CHANGES

`/api/cart/**` is neither `/api/auth/**`, `/api/admin/**`, nor a public `GET /api/products|categories`, so it falls through to `anyRequest().authenticated()`. Missing/invalid token → 401 via `RestAuthEntryPoint`. No rule needed.

---

## 3. Architecture Decision Records

### ADR-1 — Concurrency on `POST /api/cart/items`: merge-first + single retry in a SEPARATE transaction

**Decision.** Default path is **merge-first**: read `findByCartAndProduct`; if present, sum quantity (no insert, never touches the constraint). The `UNIQUE(cart_id, product_id)` constraint is the integrity backstop for the rare concurrent double-insert. When two requests race and both take the insert branch, the second `saveAndFlush` throws `DataIntegrityViolationException`; the operation is then retried as a merge **in a fresh transaction**.

**Where the catch lives (exact).** In the **non-transactional** orchestrator `CartServiceImpl.addItem(...)`, wrapping the call to the proxied `self.attemptAddItem(...)`. On catch it calls the proxied `self.mergeOnConflict(...)`. The catch is deliberately OUTSIDE any `@Transactional` method.

**Why it MUST be a separate transaction (the load-bearing gotcha).**
1. PostgreSQL aborts the *entire* transaction on a unique violation (`25P02`: "current transaction is aborted, commands ignored until end of transaction block"). Re-querying/merging in the SAME transaction is impossible — every subsequent statement fails.
2. Hibernate marks that transaction rollback-only after the violation. If you `catch` the exception **inside** the same `@Transactional` method and return normally, Spring throws `UnexpectedRollbackException` at commit — the swallowed exception re-surfaces.
3. Therefore: the failing unit (`attemptAddItem`, tx1) must let the exception **propagate** so tx1 rolls back cleanly; the catch sits in a non-transactional caller; the retry (`mergeOnConflict`) runs as an independent tx2.

`saveAndFlush` (not `save`) is mandatory on the insert so the violation surfaces synchronously at the catch point, not deferred to commit. Self-injection (`@Lazy CartService self`) is required because Spring's transactional proxy is bypassed on plain `this.method()` self-invocation. The same single catch also covers the `UNIQUE(user_id)` cart-creation race, because `mergeOnConflict` re-runs `getOrCreateCart` (which now finds the committed cart) in tx2.

**Rejected alternatives.**
- *Same-transaction catch-and-merge* — WRONG on Postgres (aborted tx + `UnexpectedRollbackException`). This is the naive reading of "catch → re-fetch"; explicitly rejected.
- *Native `INSERT ... ON CONFLICT DO UPDATE`* — atomic and elegant, but bypasses JPA entity lifecycle, is awkward to unit-test with Mockito, and diverges from the manual-mapping `catalogo` style. Rejected for consistency.
- *Spring Retry (`@Retryable`)* — clean, but a NEW dependency; proposal mandates zero new deps. Rejected.
- *Optimistic/pessimistic locking (`@Version` / `SELECT FOR UPDATE`)* — needs a schema/version column or row-lock contention on the cart; overkill for the "Med" concurrency profile. Rejected.
- *Equivalent without self-injection:* a `TransactionTemplate` (two `execute(...)` blocks) achieves the same two-transaction shape. Noted as an acceptable substitute if the team prefers to avoid the self-proxy; the self-proxy is the primary recommendation because it keeps each unit as a plain `@Transactional` method.

### ADR-2 — Mutation response shapes

- `POST /items` → **201** + full `CartResponse`
- `PUT /items/{id}` → **200** + full `CartResponse`
- `DELETE /items/{id}` → **204** (no body)
- `DELETE /cart` → **204** (no body)
- `GET /cart` → **200** + full `CartResponse`

**Rationale.** Add/update change content, so returning the recomputed cart (items + total + updatedAt) saves the client a follow-up `GET`. Deletions are terminal; 204 is the idiomatic "success, nothing to return" — the client already knows the item/cart is gone and can re-`GET` if it wants totals. The minor asymmetry (mutations return a body, deletions don't) is an accepted, deliberate REST tradeoff.

### ADR-3 — IDOR protection on item-scoped endpoints

`PUT /items/{itemId}` and `DELETE /items/{itemId}` take an item id from the URL. Guard: load the item by id, then `requireOwnedItem` verifies `item.getCart().getUser().getId().equals(currentUser.getId())`. On mismatch → **404 `ResourceNotFoundException`** (NOT 403) so the API does not reveal that someone else's item exists. The cart itself is always loaded from the token's user, never from a URL id.

### ADR-4 — `updated_at` managed in the service, no JPA callback

`CartServiceImpl.touch(cart)` calls `cart.setUpdatedAt(Instant.now())` immediately before every `cartRepository.save(cart)` on a write path. No `@PreUpdate`/`@PrePersist` and no DB trigger — keeps the entity a dumb data holder and the timing explicit/testable (asserted via `ArgumentCaptor`). A new cart sets both `createdAt` and `updatedAt` at creation.

### ADR-5 — No `@OneToMany` on `Cart`; items loaded by query

Items are read with `cartItemRepository.findByCart(cart)` and passed into `CartMapper.toResponse(cart, items)`. Avoids N+1 and `LazyInitializationException`, and keeps the response mapping explicit. (Carried from exploration/proposal.)

### ADR-6 — Price read live, never snapshotted in `cart_item`

`CartItemResponse.price`/`subtotal` and `CartResponse.total` are computed from the current `Product.price` at response time. Price freezing belongs to checkout (`order_items.unit_price`), which is out of scope.

---

## 4. Test Strategy

Strict TDD, RED → GREEN → REFACTOR per group. Runner: `cd backend && ./mvnw test`. NO H2 — integration uses Testcontainers Postgres 16.

### 4.1 Unit — `service/CartServiceImplTest` (JUnit 5 + Mockito, no Spring, no DB)
Construct `new CartServiceImpl(mockRepos..., new CartMapper(), selfMock)`. Mocks: `CartRepository`, `CartItemRepository`, `ProductRepository`, `UserRepository`. `CartMapper` is the real object (pure mapping). The `self` collaborator is a Mockito mock used only for the `addItem` wiring test.
- `getCart`: empty cart synthesized when `findByUser` empty (no save); mapped cart with items + correct `total` when present.
- `attemptAddItem`: insert when product absent (verify `saveAndFlush`); merge/sum when present; `qty > stock` → `InsufficientStockException`; inactive/missing product → `ResourceNotFoundException`; lazy cart created when absent; `updatedAt` set before save (`ArgumentCaptor<Cart>`).
- `addItem` wiring: stub `self.attemptAddItem(...)` to throw `DataIntegrityViolationException` → verify `self.mergeOnConflict(...)` is invoked and its result returned.
- `mergeOnConflict`: merges onto the now-existing row; re-validates stock against the summed quantity.
- `updateItem`: replaces quantity; IDOR (item owned by another user) → `ResourceNotFoundException`; item missing → 404; inactive product → 404; `qty > stock` → 422; `updatedAt` set.
- `removeItem`: deletes the item; IDOR → 404; missing → 404; `updatedAt` set.
- `clearCart`: `deleteByCart` invoked + `updatedAt` set when cart exists; no-op when absent.

### 4.2 Web slice — `controller/CartControllerTest` (`@WebMvcTest`, `addFilters=false`, exclude `JwtAuthenticationFilter`, `@MockBean CartService`)
Principal supplied via `spring-security-test` (already a dependency): `@WithMockUser(username = "user@krypton.pe")` or `.with(user("user@krypton.pe"))`. Assert HTTP contract only:
- `GET /api/cart` → 200, body shape (`cartId`, `items[]`, `total`, `updatedAt`).
- `POST /api/cart/items` → 201; missing `productId`/`quantity` or `quantity < 1` → 400 (service never called — `verify(..., never())`).
- `PUT /api/cart/items/{id}` → 200; `quantity = 0` → 400.
- `DELETE /api/cart/items/{id}` → 204; `DELETE /api/cart` → 204.
- Service-thrown exceptions map correctly: `InsufficientStockException` → 422; `ResourceNotFoundException` → 404 (relies on `GlobalExceptionHandler` import in the slice).
(401/403 are NOT tested here — security is disabled in the slice; covered in IT.)

### 4.3 Integration — `CartIntegrationTest extends AbstractIntegrationTest` (`@AutoConfigureMockMvc`, real Postgres, full JWT chain)
Reuse the `CatalogIntegrationTest` helper style: `adminToken()`, `clientToken(email, pwd)`, `bearer(...)`, `createCategory`, `createProduct`. Use SKU/name prefix `IT-` for FK-ordered `@AfterEach` cleanup: delete `cart_item` → `cart` (rows of the test user) → products → categories; never touch the V3 admin seed.
- **401**: any `/api/cart*` without token → 401.
- **Lazy create + merge**: first `POST /items` creates the cart (201); repeating the same product SUMS quantity (one row, summed qty) — proves the UNIQUE constraint + merge.
- **Stock check**: `quantity > stock` → 422; inactive product (soft-deleted via admin) → 404.
- **PUT**: replaces quantity (200); `quantity = 0` → 400.
- **DELETE item / DELETE cart**: 204; cart emptied / single item removed (verified via follow-up `GET`).
- **Anti-IDOR**: user A creates an item; user B's `PUT`/`DELETE` on A's `itemId` → 404; user B's `GET` never shows A's items.
- **UNIQUE constraint (V4)**: concurrent/duplicate add resolves to a single row with summed quantity (no duplicate rows), exercising the merge-on-conflict path against real Postgres.

---

## 5. Build Order (Strict TDD, bottom-up)

1. **V4 migration** — `V4__add_cart_item_unique.sql`. (Schema first; verified live by the IT later.)
2. **Repositories** — add custom methods to `CartRepository` / `CartItemRepository`. (No dedicated unit test; exercised via service + IT.)
3. **Exception + handler** — `InsufficientStockException` + `GlobalExceptionHandler` entry (422).
4. **DTOs + mapper** — `CartItemRequest`, `UpdateQuantityRequest`, `CartItemResponse`, `CartResponse`, `CartMapper`. (Mapper covered indirectly by service unit tests.)
5. **Service** — `CartService` + `CartServiceImpl` with `CartServiceImplTest` FIRST (RED → GREEN → REFACTOR): merge-first, validations, IDOR, updatedAt, concurrency wiring.
6. **Controller** — `CartController` with `CartControllerTest` FIRST (web slice): status codes, validation, exception mapping.
7. **Integration** — `CartIntegrationTest`: full JWT chain, lazy create, merge, stock, IDOR, DELETEs, V4 uniqueness.

Each group: write the failing test, make it pass, refactor. Run `cd backend && ./mvnw test` per group.

---

## 6. Affected Files (design-level)

| File | Change |
|------|--------|
| `resources/db/migration/V4__add_cart_item_unique.sql` | New |
| `repository/CartRepository.java` | Mod — `findByUser` |
| `repository/CartItemRepository.java` | Mod — `findByCartAndProduct`, `findByCart`, `deleteByCart` |
| `dto/request/CartItemRequest.java` | New |
| `dto/request/UpdateQuantityRequest.java` | New |
| `dto/response/CartItemResponse.java` | New |
| `dto/response/CartResponse.java` | New |
| `mapper/CartMapper.java` | New |
| `exception/InsufficientStockException.java` | New |
| `exception/GlobalExceptionHandler.java` | Mod — 422 handler |
| `service/CartService.java` | New |
| `service/impl/CartServiceImpl.java` | New |
| `controller/CartController.java` | New |
| `model/Cart.java`, `model/CartItem.java` | None |
| `config/SecurityConfig.java` | None |
| `test/.../service/CartServiceImplTest.java` | New |
| `test/.../controller/CartControllerTest.java` | New |
| `test/.../CartIntegrationTest.java` | New |

---

## 7. Open Risks / Assumptions for Tasks Phase

- **Self-injection acceptance**: ADR-1 recommends `@Lazy CartService self`. If the team rejects self-injection on style grounds, switch to the `TransactionTemplate` variant (same two-transaction semantics) — flagged so the tasks phase can pick the concrete shape.
- **Unit-testing the concurrency path**: the true race is validated in the IT; the unit test only verifies the `addItem` try/catch WIRING (self mock). This split is intentional — confirm it satisfies the Strict-TDD coverage expectation.
- **`getOrCreateCart` scope narrowed**: GET does NOT persist a cart (proposal API rule), so `getOrCreateCart` is write-path only. This refines task point #4.
- **Stock TOCTOU**: stock is read-only outside checkout (out of scope), so there is no add-time stock race to guard. If a future feature mutates `products.stock`, revisit `validateStock`.
