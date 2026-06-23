# Tasks: Carrito (Shopping Cart)

> Strict TDD — RED → GREEN → REFACTOR per task group.
> Runner: `cd backend && ./mvnw test`
> Every phase must leave the test suite GREEN before starting the next.
> Satisfies specs: `cart-management/spec.md` and `persistence-schema/spec.md`.

---

## Phase 1 — Schema integrity (V4 migration)

> Prerequisite for all code: the DB constraint must exist before the service's
> UNIQUE-violation catch is meaningful. Apply and verify before writing any Java.

- [x] P1.1: Write `backend/src/main/resources/db/migration/V4__add_cart_item_unique.sql`
      with `ALTER TABLE cart_item ADD CONSTRAINT uq_cart_item_cart_product UNIQUE (cart_id, product_id);`
      [→ spec: persistence-schema / Unique Constraint]
- [x] P1.2: Verify `./mvnw test` passes — Flyway applies V4 cleanly, `ddl-auto: validate` still agrees with schema
      [→ spec: persistence-schema / V4 migration applied cleanly]

---

## Phase 2 — Repository custom queries

> Spring Data derives these queries; no `@Query`/`@Modifying` needed.
> No dedicated unit test — exercised indirectly by service tests (Phase 5) and IT (Phase 7).

- [x] P2.1: Add `Optional<Cart> findByUser(User user)` to `CartRepository`
      [→ design §2.3]
- [x] P2.2: Add `Optional<CartItem> findByCartAndProduct(Cart cart, Product product)` to `CartItemRepository`
      [→ design §2.3]
- [x] P2.3: Add `List<CartItem> findByCart(Cart cart)` to `CartItemRepository`
      [→ design §2.3]
- [x] P2.4: Add `void deleteByCart(Cart cart)` to `CartItemRepository`
      [→ design §2.3]
- [x] P2.5: Verify `./mvnw test` — compile OK, Spring context loads, no regression

---

## Phase 3 — DTOs and Mapper

> RED: write `CartMapperTest` before `CartMapper`. GREEN: implement until tests pass. REFACTOR.
> Records are immutable and have no logic; only the mapper warrants a dedicated unit test.

- [x] P3.1: Create `dto/request/CartItemRequest.java` record:
      `@NotNull Long productId`, `@NotNull @Min(1) Integer quantity`
      [→ spec: cart-management / Add Item Validation; design §2.4]
- [x] P3.2: Create `dto/request/UpdateQuantityRequest.java` record:
      `@NotNull @Min(1) Integer quantity`
      [→ spec: cart-management / Update Item Quantity; design §2.4]
- [x] P3.3: Create `dto/response/CartItemResponse.java` record:
      `Long itemId, Long productId, String productName, String sku, BigDecimal price, int quantity, BigDecimal subtotal`
      [→ design §2.4]
- [x] P3.4: Create `dto/response/CartResponse.java` record:
      `Long cartId, List<CartItemResponse> items, BigDecimal total, Instant updatedAt`
      (`cartId` and `updatedAt` are null for a synthesized empty cart)
      [→ spec: cart-management / Retrieve Cart / No cart yet; design §2.4]
- [x] P3.5: Write `test/.../mapper/CartMapperTest.java` (RED):
      test `toItemResponse` — subtotal = price × quantity;
      test `toResponse` — total = sum of all subtotals;
      test `emptyCart()` — cartId=null, items=[], total=ZERO, updatedAt=null
      [→ design §2.5 / §4.1]
- [x] P3.6: Create `mapper/CartMapper.java` (`@Component`, manual mapping):
      `CartItemResponse toItemResponse(CartItem item)`,
      `CartResponse toResponse(Cart cart, List<CartItem> items)`,
      `CartResponse emptyCart()`
      (price/productName/sku read from `item.getProduct()`; no `@OneToMany` — items passed in explicitly)
      [→ design §2.5; ADR-5]
- [x] P3.7: Verify `./mvnw test` — `CartMapperTest` GREEN, no regression

---

## Phase 4 — Exception and handler

> RED: confirm the handler is exercised by the web-slice test added in Phase 6.
> For now, write the exception class and the handler entry; verify compile + existing tests.

- [x] P4.1: Create `exception/InsufficientStockException.java`
      extending `RuntimeException` with single-String constructor
      [→ spec: cart-management / Add Item — Insufficient Stock; design §2.6]
- [x] P4.2: Add handler to `GlobalExceptionHandler`:
      `@ExceptionHandler(InsufficientStockException.class)` → `ResponseEntity.status(422).body(new ApiError(422, ex.getMessage()))`
      (mirror the existing `LastAdminException` handler)
      [→ design §2.6]
- [x] P4.3: Verify `./mvnw test` — compile OK, existing handler tests still GREEN

---

## Phase 5 — Service (Strict TDD: RED → GREEN → REFACTOR per sub-group)

> Write the failing unit test FIRST, then write the minimal implementation to make it pass.
> Use `new CartServiceImpl(mockCartRepo, mockCartItemRepo, mockProductRepo, mockUserRepo, new CartMapper(), selfMock)`.
> `CartMapper` is the real object (pure mapping — no mocks needed). `self` is a Mockito mock.
> Run `./mvnw test` at the end of the full phase.

- [x] P5.1: Create `service/CartService.java` interface with 5 methods:
      `CartResponse getCart(String email)`,
      `CartResponse addItem(String email, CartItemRequest request)`,
      `CartResponse updateItem(String email, Long itemId, UpdateQuantityRequest request)`,
      `void removeItem(String email, Long itemId)`,
      `void clearCart(String email)`
      [→ design §2.7]
- [x] P5.2: Create `service/impl/CartServiceImpl.java` skeleton:
      `@Service` class, constructor-inject `CartRepository`, `CartItemRepository`,
      `ProductRepository`, `UserRepository`, `CartMapper`, `@Lazy CartService self`;
      declare all private helpers as stubs; implement interface with `throw new UnsupportedOperationException()`
      [→ design §2.8; ADR-1]
- [x] P5.3: Write test (RED) for `getCart` — empty case (findByUser empty → `emptyCart()`, NO save called);
      and present case (findByUser present, items loaded → `CartResponse` with correct total);
      then implement `getCart` + private helpers `resolveUser`, `currentCart`
      [→ spec: cart-management / Retrieve Cart; design §2.8 getCart]
- [x] P5.4: Write test (RED) for `attemptAddItem` — insert path (product not in cart):
      `findByCartAndProduct` empty → new `CartItem` saved via `saveAndFlush`; `touch(cart)` called;
      then implement `getOrCreateCart`, `resolveActiveProduct`, `validateStock`, `touch`, `attemptAddItem`
      [→ spec: cart-management / Add Item / First item; design §2.8; ADR-4]
- [x] P5.5: Write test (RED) for `attemptAddItem` — merge path (same product already in cart):
      `findByCartAndProduct` present → quantities summed, `save` (not `saveAndFlush`), `touch` called;
      then make tests GREEN
      [→ spec: cart-management / Add Item / Same product added again; design §2.8]
- [x] P5.6: Write test (RED) for `attemptAddItem` — stock exceeded:
      summed qty > `product.getStock()` → `InsufficientStockException` thrown, no save;
      then implement stock guard in `validateStock`
      [→ spec: cart-management / Add Item — Insufficient Stock]
- [x] P5.7: Write test (RED) for `attemptAddItem` — inactive/missing product:
      `resolveActiveProduct` with `active=false` or absent id → `ResourceNotFoundException`;
      then implement active check in `resolveActiveProduct`
      [→ spec: cart-management / Add Item — Inactive Product]
- [x] P5.8: Write test (RED) for `addItem` concurrency wiring:
      stub `self.attemptAddItem(...)` to throw `DataIntegrityViolationException`;
      verify `self.mergeOnConflict(...)` is called and its return value is returned;
      implement the non-transactional `addItem` try/catch orchestrator;
      implement `mergeOnConflict` (re-fetch, sum, validate stock, save, touch)
      [→ design §2.8; ADR-1]
- [x] P5.9: Write test (RED) for `updateItem` — happy path:
      item found + owned by user → quantity replaced, `touch`, returns `CartResponse`;
      also test IDOR (item owned by different user → `ResourceNotFoundException`),
      item not found → 404, inactive product → 404, qty > stock → 422;
      then implement `updateItem` and `requireOwnedItem`
      [→ spec: cart-management / Update Item Quantity, Owner Isolation, Insufficient Stock; design §2.8; ADR-3]
- [x] P5.10: Write test (RED) for `removeItem` — happy path: item deleted, `touch` called;
      also test IDOR → 404, item not found → 404;
      then implement `removeItem`
      [→ spec: cart-management / Remove Single Item; design §2.8]
- [x] P5.11: Write test (RED) for `clearCart` — cart present: `deleteByCart` invoked, `touch` called;
      cart absent: no-op, no exception;
      then implement `clearCart`
      [→ spec: cart-management / Clear Cart; design §2.8]
- [x] P5.12: Verify `./mvnw test` — all unit tests GREEN (service + mapper + prior)

---

## Phase 6 — Controller (web slice, `@WebMvcTest`)

> RED: write `CartControllerTest` before `CartController` endpoints.
> `@WebMvcTest(CartController.class, excludeFilters = @ComponentScan.Filter(JwtAuthenticationFilter.class))`
> `@MockBean CartService`. Principal via `@WithMockUser(username = "user@krypton.pe")`.
> `GlobalExceptionHandler` must be included in the slice for 422/404 to be asserted.

- [x] P6.1: Write `test/.../controller/CartControllerTest.java` (RED) — `GET /api/cart` → 200, body has `items` and `total`
      [→ spec: cart-management / Retrieve Cart]
- [x] P6.2: Add test (RED) — `POST /api/cart/items` with valid body → 201 and `CartResponse`
      [→ spec: cart-management / Add Item]
- [x] P6.3: Add test (RED) — `POST /api/cart/items` with `quantity=0` → 400 (service never called: `verify(cartService, never()).addItem(...)`);
      `POST` with missing `productId` → 400
      [→ spec: cart-management / Add Item Validation]
- [x] P6.4: Add test (RED) — `PUT /api/cart/items/{itemId}` with valid body → 200;
      `PUT` with `quantity=0` → 400
      [→ spec: cart-management / Update Item Quantity]
- [x] P6.5: Add test (RED) — `DELETE /api/cart/items/{itemId}` → 204 (no body)
      [→ spec: cart-management / Remove Single Item]
- [x] P6.6: Add test (RED) — `DELETE /api/cart` → 204 (no body)
      [→ spec: cart-management / Clear Cart]
- [x] P6.7: Add test (RED) — `InsufficientStockException` from service → 422;
      `ResourceNotFoundException` from service → 404
      [→ spec: cart-management / Add Item — Insufficient Stock / Inactive Product]
- [x] P6.8: Create `controller/CartController.java`:
      `@RestController`, `@RequestMapping("/api/cart")`, `@RequiredArgsConstructor`;
      `GET` → `getCart(email)` → 200;
      `POST /items` → `addItem(email, @Valid body)` → `@ResponseStatus(CREATED)`;
      `PUT /items/{itemId}` → `updateItem(email, itemId, @Valid body)` → 200;
      `DELETE /items/{itemId}` → `removeItem(email, itemId)` → `@ResponseStatus(NO_CONTENT)`;
      `DELETE` → `clearCart(email)` → `@ResponseStatus(NO_CONTENT)`;
      principal extracted via `@AuthenticationPrincipal UserDetails principal` → `principal.getUsername()`
      [→ design §2.9; ADR-2]
- [x] P6.9: Verify `./mvnw test` — all web-slice tests GREEN, no regression

---

## Phase 7 — Integration tests (Testcontainers, full JWT chain)

> Extend `AbstractIntegrationTest`. `@AutoConfigureMockMvc`. Real Postgres 16 singleton.
> Full JWT chain active. `@AfterEach` cleanup in FK order: `cart_item → cart → products → categories`.
> Never touch the V3 admin seed. Prefix test data with `IT-CART-` (SKU) and `IT-Cart-` (category name).
> These tests also exercise the real V4 UNIQUE constraint and the merge-on-conflict path.

- [x] P7.1: Write `test/.../CartIntegrationTest.java` skeleton:
      extends `AbstractIntegrationTest`, `@AutoConfigureMockMvc`;
      inject `MockMvc`, `ObjectMapper`, `CartItemRepository`, `CartRepository`, `ProductRepository`, `CategoryRepository`;
      add `@AfterEach cleanupCartRows()`: delete `cart_item` rows whose cart belongs to test user, then `cart`, then `IT-CART-` products, then `IT-Cart-` categories;
      reuse `adminToken()`, `clientToken(email, pwd)`, `bearer(token)`, `createCategory`, `createProduct` helpers (copy or move to a shared base)
      [→ design §4.3]
- [x] P7.2: Add IT — unauthenticated request to `GET /api/cart` (and at least one POST) → 401
      [→ spec: cart-management / Authentication Required]
- [x] P7.3: Add IT — `GET /api/cart` with valid token and no prior cart → 200, `items` is empty array
      [→ spec: cart-management / No cart yet — empty response]
- [x] P7.4: Add IT — `POST /api/cart/items` creates cart lazily on first call → 201, item present in response;
      follow-up `GET /api/cart` shows the item
      [→ spec: cart-management / First item — cart created lazily]
- [x] P7.5: Add IT — `POST` same product twice → single row with summed quantity (merge path + UNIQUE constraint exercised):
      second `POST` → 201; `GET` shows one item, quantity = sum; no duplicate rows in `cart_item`
      [→ spec: cart-management / Same product added again; persistence-schema / Duplicate cart item rejected at DB level]
- [x] P7.6: Add IT — `POST` with `quantity > product.stock` → 422
      [→ spec: cart-management / Stock exceeded on add]
- [x] P7.7: Add IT — inactive product (admin soft-deletes it) then `POST` → 404
      [→ spec: cart-management / Inactive product rejected]
- [x] P7.8: Add IT — `PUT /api/cart/items/{itemId}` replaces quantity → 200, `GET` confirms new quantity;
      `PUT` with `quantity=0` → 400
      [→ spec: cart-management / Quantity replaced; quantity = 0 in PUT rejected]
- [x] P7.9: Add IT — `PUT` with quantity > stock → 422
      [→ spec: cart-management / Update exceeds stock]
- [x] P7.10: Add IT — anti-IDOR: user A adds an item; user B's `PUT /api/cart/items/{A's itemId}` → 404;
      user B's `DELETE /api/cart/items/{A's itemId}` → 404;
      user B's `GET /api/cart` shows only their own items
      [→ spec: cart-management / Owner Isolation / Cart Identity via Token]
- [x] P7.11: Add IT — `DELETE /api/cart/items/{itemId}` removes that item (204); `GET` shows remaining items intact
      [→ spec: cart-management / Item removed successfully]
- [x] P7.12: Add IT — `DELETE /api/cart` removes all items (204); `GET` returns empty items array
      [→ spec: cart-management / Cart cleared]
- [x] P7.13: Add IT — `DELETE /api/cart` when no cart exists → 204 (idempotent, no error)
      [→ spec: cart-management / Empty cart cleared idempotently]
- [x] P7.14: Add IT — `GET` does NOT mutate `cart.updated_at`: record `updatedAt` before and after a `GET` → timestamps equal
      [→ spec: persistence-schema / GET does not mutate updated_at]
- [x] P7.15: Add IT — write op (`POST /items`) does advance `cart.updated_at` relative to `createdAt`
      [→ spec: persistence-schema / updated_at advances after add]
- [x] P7.16: Verify `./mvnw test` — ALL tests GREEN (unit + web-slice + integration, no regression)

---

## Summary

| Phase | Tasks | Parallel with |
|-------|-------|---------------|
| P1 Schema | P1.1–P1.2 | — (must be first) |
| P2 Repos | P2.1–P2.5 | sequential after P1 |
| P3 DTOs+Mapper | P3.1–P3.7 | can start alongside P2 (no DB deps) |
| P4 Exception | P4.1–P4.3 | can start alongside P2 and P3 |
| P5 Service | P5.1–P5.12 | sequential after P2, P3, P4 |
| P6 Controller | P6.1–P6.9 | sequential after P5 |
| P7 Integration | P7.1–P7.16 | sequential after P6 |

Total: **52 tasks** across 7 phases.
Phases 2, 3, and 4 can be developed in parallel (no inter-dependency).
Phases 5, 6, and 7 are strictly sequential (each builds on the prior layer).
