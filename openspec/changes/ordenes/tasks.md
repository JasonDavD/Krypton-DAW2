# Tasks: Órdenes / Checkout con Pago Simulado

> Strict TDD throughout. Every phase ends with `cd backend && ./mvnw test` — ALL tests must be
> GREEN before starting the next phase. Build direction: bottom-up (repos → DTOs/enums/exceptions
> → service → controllers → integration).
>
> Orchestrator override: `OrderResponse` MUST include `Long userId` (overrides design ADR-4).
> Satisfies REQ-OM-10 (admin cannot attribute orders without userId) and the proposal API Contract.

---

## Phase 1 — Repository Queries

> No behavior yet — derived-query method signatures only. They compile and the existing test
> suite stays GREEN. Parallel tasks: P1.1, P1.2, P1.3 can be done in any order.

- [x] P1.1: Add `findByUserOrderByOrderDateDesc(User user)` and `findByIdAndUser(Long id, User user)` to `OrderRepository` — satisfies REQ-OM-05, REQ-OM-06, REQ-OM-09
- [x] P1.2: Add `findByOrder(Order order)` to `OrderItemRepository` — satisfies REQ-OM-01, REQ-OM-06, REQ-OM-11
- [x] P1.3: Add `@Lock(PESSIMISTIC_WRITE) @Query("SELECT p FROM Product p WHERE p.id = :id") Optional<Product> findByIdWithLock(@Param("id") Long id)` to `ProductRepository` — satisfies REQ-OM-01 (race condition guard)
- [x] P1.4: Run `cd backend && ./mvnw test` — expect GREEN (no new tests, existing suite unchanged)

---

## Phase 2 — DTOs, Enums, and Mapper

> Declarative artifacts + one pure unit test for the mapper. No service wiring yet.
> P2.1 through P2.5 are parallel; P2.6 depends on P2.1–P2.5; P2.7 depends on P2.6.

- [x] P2.1: Create `model/enums/PaymentMethod.java` enum with values `CREDIT_CARD`, `YAPE`, `EFECTIVO` — satisfies REQ-OM-07
- [x] P2.2: Create `dto/request/PaymentRequest.java` record: `@NotNull PaymentMethod method` — satisfies REQ-OM-07
- [x] P2.3: Create `dto/request/OrderStatusUpdateRequest.java` record: `@NotNull OrderStatus status` — satisfies REQ-OM-12
- [x] P2.4: Create `dto/response/OrderItemResponse.java` record: `Long id, Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal` — satisfies REQ-OM-01, REQ-OM-05, REQ-OM-06
- [x] P2.5: Create `dto/response/OrderResponse.java` record: `Long id, Long userId, Instant orderDate, String status, BigDecimal total, List<OrderItemResponse> items` — satisfies REQ-OM-01, REQ-OM-10 (userId required for admin attribution; orchestrator override of ADR-4)
- [x] P2.6: Create `mapper/OrderMapper.java` `@Component` with `toItemResponse(OrderItem)` (subtotal = unitPrice × quantity) and `toResponse(Order, List<OrderItem>)` (status = enum.name(), total from order.getTotal(), unitPrice from item.getUnitPrice() never product.getPrice(), userId from order.getUser().getId()) — satisfies REQ-OM-04, ADR-5
- [x] P2.7: Write `OrderMapperTest` (unit, Mockito-free): verify subtotal equals unitPrice × quantity; verify total comes from order.getTotal() not recomputed; verify status is the enum name string; verify userId equals order.getUser().getId(); verify unitPrice is the snapshot and not affected by a hypothetical later price change — satisfies REQ-OM-04
- [x] P2.8: Run `cd backend && ./mvnw test` — expect GREEN

---

## Phase 3 — Exceptions and Handler Entries

> Two new exception classes + two handler entries in GlobalExceptionHandler.
> P3.1 and P3.2 are parallel; P3.3 depends on both.

- [x] P3.1: Create `exception/EmptyCartException.java` extending `RuntimeException` with single `(String message)` constructor — satisfies REQ-OM-02
- [x] P3.2: Create `exception/OrderStatusTransitionException.java` extending `RuntimeException` with single `(String message)` constructor — satisfies REQ-OM-08
- [x] P3.3: Add handler for `EmptyCartException` → `400 BAD_REQUEST` and handler for `OrderStatusTransitionException` → `422 UNPROCESSABLE_ENTITY` in `GlobalExceptionHandler`, following existing `ApiError(status, ex.getMessage())` pattern — satisfies REQ-OM-02, REQ-OM-08
- [x] P3.4: Run `cd backend && ./mvnw test` — expect GREEN

---

## Phase 4 — Service Layer (Strict TDD: RED → GREEN per group)

> This is the behavioral core. Write failing tests first, then implement until GREEN.
> Groups within this phase are sequential (each group's impl unlocks the next group's test scope).

- [x] P4.1: Write `OrderService` interface with method signatures: `checkout(String email)`, `getMyOrders(String email)`, `getMyOrder(String email, Long orderId)`, `pay(String email, Long orderId, PaymentRequest request)`, `getAllOrders(Pageable pageable)`, `getOrder(Long orderId)`, `updateStatus(Long orderId, OrderStatus newStatus)` — all return types as per design §2.5
- [x] P4.2: Create `OrderServiceImpl` skeleton `@Service` with constructor injection for `OrderRepository`, `OrderItemRepository`, `ProductRepository`, `StockMovementRepository`, `CartRepository`, `CartItemRepository`, `UserRepository`, `CartService`, `OrderMapper` — all methods throw `UnsupportedOperationException`
- [x] P4.3: Write `OrderServiceImplTest` checkout group (RED): happy-path checkout asserts order saved, one OrderItem per cart line with unitPrice snapshot, stock decremented, one SALIDA StockMovement per item with correct reason/reference, clearCart invoked; verify no order/movement saved when stock insufficient (InsufficientStockException); verify EmptyCartException when cart has zero items; verify ResourceNotFoundException when product not found — satisfies REQ-OM-01, REQ-OM-02, REQ-OM-03
- [x] P4.4: Implement `OrderServiceImpl.checkout`: two-pass validate-then-mutate (pass A: lock+validate all products + accumulate total; pass B: save Order, per item save OrderItem + decrement stock + save StockMovement(SALIDA, reason="Venta orden #"+orderId, reference="ORDER-"+orderId, createdBy=null) + clearCart) — satisfies REQ-OM-01, REQ-OM-02, REQ-OM-03, REQ-OM-04; tests go GREEN
- [x] P4.5: Write `OrderServiceImplTest` read group (RED): getMyOrders returns only the authenticated user's orders ordered by date desc; getMyOrder returns detail when owner matches; getMyOrder throws ResourceNotFoundException (404) when order belongs to another user (IDOR) — satisfies REQ-OM-05, REQ-OM-06
- [x] P4.6: Implement `OrderServiceImpl.getMyOrders` and `getMyOrder` with `@Transactional(readOnly=true)` — tests go GREEN
- [x] P4.7: Write `OrderServiceImplTest` pay group (RED): pay transitions PENDIENTE→CONFIRMADA and saves; pay throws OrderStatusTransitionException when status is CONFIRMADA; pay throws OrderStatusTransitionException when status is CANCELADA; pay throws ResourceNotFoundException when order belongs to another user — satisfies REQ-OM-07, REQ-OM-08, REQ-OM-09
- [x] P4.8: Implement `OrderServiceImpl.pay` with `@Transactional`: findByIdAndUser (IDOR→404), guard PENDIENTE-only (else 422), setStatus(CONFIRMADA), save — tests go GREEN
- [x] P4.9: Write `OrderServiceImplTest` admin group (RED): getAllOrders returns Page<OrderResponse>; getOrder returns any order by id; getOrder throws ResourceNotFoundException for unknown id; updateStatus sets arbitrary target status with no transition guard — satisfies REQ-OM-10, REQ-OM-11, REQ-OM-12
- [x] P4.10: Implement `OrderServiceImpl.getAllOrders` and `getOrder` with `@Transactional(readOnly=true)` and `updateStatus` with `@Transactional` (findById, no guard, setStatus, save) — tests go GREEN
- [x] P4.11: Run `cd backend && ./mvnw test` — expect GREEN (all unit tests + previous suite)

---

## Phase 5 — Controllers (Web Slice)

> @WebMvcTest with JWT filter excluded and OrderService mocked. Write tests first (RED), then
> wire controllers (GREEN). P5.1–P5.4 (OrderController tests) and P5.5–P5.7 (AdminOrderController
> tests) can be written in parallel; controllers are implemented after both test classes compile.

- [x] P5.1: Write `OrderControllerTest` happy-path cases (@WebMvcTest): POST /api/orders/checkout → 201 + OrderResponse JSON; GET /api/orders → 200 + list; GET /api/orders/{id} → 200 + OrderResponse; POST /api/orders/{id}/pay → 200 + OrderResponse — satisfies REQ-OM-01, REQ-OM-05, REQ-OM-06, REQ-OM-07
- [x] P5.2: Write `OrderControllerTest` error cases: checkout with empty cart → 400; checkout with stock exceeded → 422; pay wrong status → 422; get/pay IDOR → 404; pay with missing method field → 400 (Bean Validation) — satisfies REQ-OM-02, REQ-OM-03, REQ-OM-08, REQ-OM-09
- [x] P5.3: Write `OrderControllerTest` auth cases: all endpoints without token → 401 — satisfies REQ-OM-13
- [x] P5.4: Write `AdminOrderControllerTest` happy-path cases (@WebMvcTest): GET /api/admin/orders?page=0&size=10 → 200 + PageResponse; GET /api/admin/orders/{id} → 200 + OrderResponse; PUT /api/admin/orders/{id}/status → 200 + OrderResponse — satisfies REQ-OM-10, REQ-OM-11, REQ-OM-12
- [x] P5.5: Write `AdminOrderControllerTest` error cases: GET admin list without token → 401; GET admin list with CLIENTE token → 403; GET admin order not found → 404; PUT status with missing status field → 400 — satisfies REQ-OM-13
- [x] P5.6: Create `OrderController` `@RestController @RequestMapping("/api/orders")` with `@AuthenticationPrincipal UserDetails principal`: POST /checkout (201), GET / (200 List), GET /{id} (200), POST /{id}/pay with `@Valid PaymentRequest` (200) — wires to OrderService — all test cases go GREEN
- [x] P5.7: Create `AdminOrderController` `@RestController @RequestMapping("/api/admin/orders")`: GET / with Pageable (200 PageResponse), GET /{id} (200), PUT /{id}/status with `@Valid OrderStatusUpdateRequest` (200) — no `@AuthenticationPrincipal` (auth enforced by SecurityConfig) — all test cases go GREEN
- [x] P5.8: Run `cd backend && ./mvnw test` — expect GREEN

---

## Phase 6 — Integration Tests (Testcontainers)

> Follows CartIntegrationTest pattern: extends AbstractIntegrationTest, @AutoConfigureMockMvc,
> singleton Postgres 16 container, full JWT chain. Test user email prefix: `it-ord-`.
> FK cleanup order in @AfterEach: stock_movement → order_items → orders → cart_item → cart → users.
> All tasks in this phase are sequential (each test depends on checkout working end-to-end).

- [x] P6.1: Create `OrderIntegrationTest` class extending `AbstractIntegrationTest` with `@AutoConfigureMockMvc`, helper methods `clientToken(email, password)`, `adminToken()`, `bearer(token)`, `createCategory`, `createProduct`, `addToCart(token, productId, qty)`, and `uniqueEmail()` with `it-ord-` prefix — infrastructure only, no test methods yet
- [x] P6.2: Implement `@AfterEach cleanupOrderRows` in FK order: delete `stock_movement` rows for IT orders, then `order_items`, then `orders`, then `cart_item`, then `cart`, then users with email prefix `it-ord-`; also delete IT-ORD- products and IT-Ord- categories — satisfies RISK-6
- [x] P6.3: Write and pass IT: full checkout flow — create product, add to cart, POST /checkout → 201; assert one `orders` row with PENDIENTE; assert one `order_items` row with correct unit_price snapshot; assert product stock decremented; assert one `stock_movement` SALIDA row with reference "ORDER-{id}"; assert cart is empty after checkout — satisfies REQ-OM-01, REQ-OM-04
- [x] P6.4: Write and pass IT: checkout rollback — product with stock=1, add qty=2 to cart, POST /checkout → 422; assert zero `orders` rows (full rollback); assert product stock unchanged — satisfies REQ-OM-03
- [x] P6.5: Write and pass IT: empty cart checkout — authenticated client with empty cart, POST /checkout → 400; assert zero `orders` rows — satisfies REQ-OM-02
- [x] P6.6: Write and pass IT: client order history — client places two orders, GET /api/orders → 200 list with exactly those two orders, newest first; another client's orders not included — satisfies REQ-OM-05
- [x] P6.7: Write and pass IT: client order detail — client places order, GET /api/orders/{id} → 200 OrderResponse with correct items and userId — satisfies REQ-OM-06
- [x] P6.8: Write and pass IT: IDOR on GET — client A places order; client B calls GET /api/orders/{orderId} → 404 (not 403) — satisfies REQ-OM-06
- [x] P6.9: Write and pass IT: IDOR on pay — client A places order; client B calls POST /api/orders/{orderId}/pay → 404 — satisfies REQ-OM-09
- [x] P6.10: Write and pass IT: pay PENDIENTE order — client places order, POST /api/orders/{id}/pay with `{"method":"YAPE"}` → 200; assert order status is CONFIRMADA in DB — satisfies REQ-OM-07
- [x] P6.11: Write and pass IT: pay CONFIRMADA order — client pays once (→ CONFIRMADA), pays again → 422; assert status remains CONFIRMADA — satisfies REQ-OM-08
- [x] P6.12: Write and pass IT: admin paginated list — two clients each place one order; admin calls GET /api/admin/orders?page=0&size=10 → 200 PageResponse with both orders; each OrderResponse includes userId — satisfies REQ-OM-10
- [x] P6.13: Write and pass IT: admin order detail — client places order; admin calls GET /api/admin/orders/{id} → 200 OrderResponse (any user's order returned) — satisfies REQ-OM-11
- [x] P6.14: Write and pass IT: admin status update — admin calls PUT /api/admin/orders/{id}/status `{"status":"CANCELADA"}` on a CONFIRMADA order → 200; assert status is CANCELADA in DB (no transition guard) — satisfies REQ-OM-12
- [x] P6.15: Write and pass IT: 401 unauthenticated — POST /api/orders/checkout without token → 401; GET /api/admin/orders without token → 401 — satisfies REQ-OM-13
- [x] P6.16: Write and pass IT: 403 client on admin endpoint — authenticated CLIENTE calls GET /api/admin/orders → 403 — satisfies REQ-OM-13
- [x] P6.17: Run `cd backend && ./mvnw test` — expect ALL GREEN (previous suite + all new tests)
