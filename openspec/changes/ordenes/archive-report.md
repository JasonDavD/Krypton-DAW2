# Archive Report: ordenes / Checkout con Pago Simulado

**Date**: 2026-06-07  
**Project**: krypton-ecommerce  
**Verdict**: PASS (224/224 tests, 0 CRITICAL)

---

## Change Summary

**Change**: ordenes  
**Intent**: Close the purchase flow by enabling atomic checkout (cart → persistent order with price snapshot and stock decrement), client order lifecycle management (history, detail with IDOR protection), simulated payment (PENDIENTE → CONFIRMADA), and admin order administration (paginated listing, detail, free-form status update).

**Phases Completed**: 6/6 (explore, proposal, spec, design, tasks, apply, verify, archive)  
**Tasks Completed**: 37/37 (all marked ✓)  
**Tests**: 52 new + 172 pre-existing = 224 total, ALL GREEN

---

## Specs Synced to Source of Truth

| Capability | Domain | Action | Details |
|------------|--------|--------|---------|
| order-management | order-management | Created | 13 requirements (REQ-OM-01 through REQ-OM-13): checkout atomicity, empty cart rejection, stock rollback with full ACID guarantee, price snapshot isolation, client history/detail with IDOR, simulated payment (PENDIENTE→CONFIRMADA), invalid transition rejection, admin listing (paginated), admin detail, admin free-form status update, auth/authz (JWT + ROLE_ADMIN) |

**File created**: `openspec/specs/order-management/spec.md`

---

## Implementation Summary

### New Components (52 tests across 5 test classes)

**Repositories (3 additions to existing repos)**:
- `ProductRepository.findByIdWithLock(Long)` — pessimistic lock (PESSIMISTIC_WRITE) to guard checkout race condition
- `OrderRepository.findByUserOrderByOrderDateDesc(User)` — client order history
- `OrderRepository.findByIdAndUser(Long, User)` — client order detail with IDOR

**Enums & DTOs (5 new)**:
- `PaymentMethod` enum (CREDIT_CARD, YAPE, EFECTIVO)
- `PaymentRequest` record (@NotNull method)
- `OrderStatusUpdateRequest` record (@NotNull status)
- `OrderItemResponse` record (id, productId, productName, quantity, unitPrice, subtotal)
- `OrderResponse` record (id, userId, orderDate, status, total, items)

**Mapper (1 new component)**:
- `OrderMapper` @Component — toItemResponse (subtotal = unitPrice × quantity), toResponse (status from enum.name(), total from order.getTotal(), userId from order.getUser().getId())

**Exceptions (2 new + 2 handler entries)**:
- `EmptyCartException` (400 BAD_REQUEST) — thrown when cart has zero items at checkout
- `OrderStatusTransitionException` (422 UNPROCESSABLE_ENTITY) — thrown when payment or status update violates business rules
- Both mapped in `GlobalExceptionHandler` following existing ApiError pattern

**Service Layer (1 interface + 1 impl, 16 unit tests)**:
- `OrderService` interface with 7 methods
- `OrderServiceImpl` @Service (Strict TDD RED→GREEN):
  - `checkout(String email)` — atomic two-pass validate-then-mutate with @Transactional: PASS A (lock+validate all products), PASS B (save Order → per-item save OrderItem + decrement stock + insert SALIDA StockMovement → clearCart)
  - `getMyOrders(String email)` — client history (readOnly)
  - `getMyOrder(String email, Long orderId)` — client detail with IDOR via findByIdAndUser → 404 if ajena
  - `pay(String email, Long orderId, PaymentRequest)` — PENDIENTE→CONFIRMADA guard (else 422)
  - `getAllOrders(Pageable)` — admin paginated list (readOnly)
  - `getOrder(Long orderId)` — admin detail (readOnly)
  - `updateStatus(Long orderId, OrderStatus newStatus)` — admin free-form status (no guard)

**Controllers (2 new + 15 web slice tests)**:
- `OrderController` @RestController("/api/orders") — uses @AuthenticationPrincipal to extract email:
  - POST /checkout (201 OrderResponse)
  - GET / (200 List<OrderResponse>)
  - GET /{id} (200 OrderResponse)
  - POST /{id}/pay with @Valid PaymentRequest (200 OrderResponse)
- `AdminOrderController` @RestController("/api/admin/orders") — ROLE_ADMIN enforced by SecurityConfig:
  - GET / with Pageable (200 PageResponse<OrderResponse>)
  - GET /{id} (200 OrderResponse)
  - PUT /{id}/status with @Valid OrderStatusUpdateRequest (200 OrderResponse)

**Integration Tests (15 tests via Testcontainers Postgres 16)**:
- Full checkout flow (create product, add to cart, checkout → 201; assert Order+OrderItem+stock decrement+SALIDA StockMovement+empty cart)
- Checkout rollback (stock insufficient → 422; assert 0 orders, stock unchanged, full ACID rollback)
- Empty cart rejection (→ 400)
- Client order history (own orders only, date DESC)
- Client order detail (own + IDOR → 404)
- IDOR on payment (another user's order → 404)
- Simulated payment (PENDIENTE→CONFIRMADA)
- Invalid transition (CONFIRMADA/CANCELADA → 422)
- Admin paginated list (all orders with userId)
- Admin order detail (any user's order)
- Admin free-form status update (any transition, no guard)
- Auth/authz (no token → 401, CLIENTE on /api/admin/** → 403)

---

## Test Coverage

| Test Class | Count | Layer |
|-----------|-------|-------|
| OrderMapperTest | 6 | Unit (Mockito-free) |
| OrderServiceImplTest | 16 | Unit (Mockito) |
| OrderControllerTest | 10 | Web Slice (@WebMvcTest) |
| AdminOrderControllerTest | 5 | Web Slice (@WebMvcTest) |
| OrderIntegrationTest | 15 | Integration (Testcontainers) |
| **Order total** | **52** | — |
| Pre-existing (catalogo, carrito, auth) | 172 | — |
| **Grand Total** | **224** | — |

**Verdict**: PASS (224 passed, 0 failed, 0 skipped)

---

## Verification Report

**Verifier**: sdd-verify (Claude Sonnet 4.6)  
**Date**: 2026-06-07  
**Test Run**: `cd backend && ./mvnw test` — BUILD SUCCESS

### Requirements Coverage

All 13 requirements (REQ-OM-01 through REQ-OM-13) PASS with multiple test vectors per requirement:

| REQ | Description | Unit | Web | IT | Status |
|-----|-------------|------|-----|-----|--------|
| REQ-OM-01 | Atomic checkout | ✓ | ✓ | ✓ | PASS |
| REQ-OM-02 | Empty cart → 400 | ✓ | ✓ | ✓ | PASS |
| REQ-OM-03 | Stock rollback (0 orders) | ✓ | ✓ | ✓ | PASS |
| REQ-OM-04 | Price snapshot isolation | ✓ | — | ✓ | PASS |
| REQ-OM-05 | Client order history | ✓ | — | ✓ | PASS |
| REQ-OM-06 | Client order detail + IDOR | ✓ | ✓ | ✓ | PASS |
| REQ-OM-07 | Simulated payment | ✓ | ✓ | ✓ | PASS |
| REQ-OM-08 | Invalid transition → 422 | ✓ | ✓ | ✓ | PASS |
| REQ-OM-09 | IDOR on payment | ✓ | ✓ | ✓ | PASS |
| REQ-OM-10 | Admin paginated list + userId | ✓ | ✓ | ✓ | PASS |
| REQ-OM-11 | Admin order detail | ✓ | ✓ | ✓ | PASS |
| REQ-OM-12 | Admin free-form status | ✓ | ✓ | ✓ | PASS |
| REQ-OM-13 | Auth/authz (JWT, ROLE_ADMIN) | — | — | ✓ | PASS |

### Issues

**CRITICAL**: 0  
**WARNING**: 1
- W-01: 401 coverage missing from web slice (OrderControllerTest + AdminOrderControllerTest use addFilters=false). Deferred to IT. Accepted trade-off per project's CartControllerTest pattern.

**SUGGESTION**: 2
- S-01: N+1 query in getAllOrders not documented in code. Recommend TODO comment in OrderServiceImpl.
- S-02: IT rollback test simulates race via stock=0 workaround (not true concurrent test). Acceptable for this scope; note if concurrency audit later.

---

## Design Decisions (10 ADRs Implemented)

| ADR | Decision | Implemented | Status |
|-----|----------|-------------|--------|
| ADR-1 | Checkout @Transactional single-write, validate-then-mutate | Two-pass (lock all, then save) | ✓ PASS |
| ADR-2 | Pessimistic lock (@Lock(PESSIMISTIC_WRITE)) in ProductRepository | findByIdWithLock with SELECT FOR UPDATE | ✓ PASS |
| ADR-3 | Minimal repo additions (findByUser, findByIdAndUser, findByOrder) | All three present | ✓ PASS |
| ADR-4 (overridden) | OrderResponse includes Long userId (orchestrator override) | Record field added, tests updated | ✓ PASS |
| ADR-5 | Manual mapper (@Component) reading item.getUnitPrice(), order.getTotal() | OrderMapper.toItemResponse/toResponse | ✓ PASS |
| ADR-6 | Two controllers (client uses principal, admin via SecurityConfig) | OrderController + AdminOrderController | ✓ PASS |
| ADR-7 | EmptyCartException(400) + OrderStatusTransitionException(422) | Both in GlobalExceptionHandler | ✓ PASS |
| ADR-8 | Payment: findByIdAndUser (IDOR→404) + PENDIENTE guard (422) | pay() method implements both | ✓ PASS |
| ADR-9 | Admin updateStatus: findById (no owner check), arbitrary transition | No guard, any status allowed | ✓ PASS |
| ADR-10 | Three test layers (unit/Mockito + web/@WebMvcTest + IT/Testcontainers) | 52 new tests across 5 classes | ✓ PASS |

---

## Artifact Store Traceability (Engram Observation IDs)

For cross-session recovery and verification, the following engram artifacts were archived:

- **sdd/ordenes/proposal**: #313 — Change intent, scope, approach, API contract, business rules, affected areas
- **sdd/ordenes/spec**: #314 — Capability spec with 13 requirements (full spec, not delta)
- **sdd/ordenes/design**: #315 — Technical design, ADRs, component map, data flow, risks, assumptions
- **sdd/ordenes/tasks**: #316 — 37 tasks across 6 phases (P1–P6), Strict TDD ordering, phase dependencies
- **sdd/ordenes/verify-report**: #318 — Test coverage (52 new + 172 pre-existing = 224 total), requirement coverage, ADR verification, issues (0 CRITICAL)

---

## Rollback Plan (Risk Mitigation)

The change is 100% additive at the codebase level. No schema migrations required (V1 covers all tables: orders, order_items, stock_movement, orders and order_items are FKs to existing users, products tables).

**To rollback**:
1. `git revert <merge-commit>` — removes all new code (OrderController, OrderService, DTOs, exceptions, etc.)
2. No database migration reversal needed — ddl-auto: validate means no automatic changes
3. `docker compose down -v` + restart locally — resets to schema V1 baseline

**Risk level**: LOW (no data loss, no schema changes, atomic within transaction)

---

## Success Criteria (All Met)

- [x] `cd backend && ./mvnw test` passes (224 passed)
- [x] POST /checkout creates order+items (snapshot), decrements stock, inserts SALIDA StockMovement, clears cart — atomically
- [x] Empty cart → 400 EmptyCartException; no order created
- [x] Stock insufficient → 422 InsufficientStockException; full rollback (0 orders, stock unchanged)
- [x] GET /api/orders only own orders, ordered by date DESC
- [x] GET /api/orders/{id} own order → 200; another user → 404 (IDOR)
- [x] POST /api/orders/{id}/pay PENDIENTE→CONFIRMADA; invalid transition → 422
- [x] GET /api/admin/orders paginated, all orders, ROLE_ADMIN enforced
- [x] PUT /api/admin/orders/{id}/status arbitrary transition (admin override)
- [x] No token → 401; CLIENTE on /api/admin/** → 403
- [x] No new dependencies in pom.xml (Testcontainers already present)
- [x] Entities never exposed; DTOs only
- [x] Bloqueo pesimista (@Lock) prevents oversell race

---

## Archive Status

**Change Folder**: `openspec/changes/ordenes/`  
**State**: ARCHIVED (all 6 phases complete, all 37 tasks complete, 224/224 tests passing)  
**Spec Synced**: `openspec/specs/order-management/spec.md` created (new capability)  
**Archive Date**: 2026-06-07  
**Cycle**: CLOSED — Ready for next change

---

## Next Steps

1. Merge the main branch (contains all ordenes changes from feat/ordenes PR)
2. Mark capability "ordenes" as ✅ LIVE in project status
3. Begin next capability (e.g., `payments` for real payment gateway, `returns` for refunds/reversals)
