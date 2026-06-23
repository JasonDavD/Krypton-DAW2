# Verify Report: ordenes / Checkout con Pago Simulado

**Date**: 2026-06-07
**Verifier**: sdd-verify (Claude Sonnet 4.6)
**Test run**: 224 passed, 0 failed, 0 skipped - BUILD SUCCESS
**Verdict**: PASS - 0 CRITICAL, 1 WARNING, 2 SUGGESTIONS

---

## Build & Tests

Tests run: 224, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS (47.1 s)

Test counts by class (new tests for this change):

| Class | Count |
|-------|-------|
| OrderMapperTest | 6 |
| OrderServiceImplTest | 16 |
| OrderControllerTest | 10 |
| AdminOrderControllerTest | 5 |
| OrderIntegrationTest | 15 |
| Order subtotal | 52 |
| Pre-existing tests | 172 |
| Grand total | 224 |

---

## Requirements

| REQ | Description | Coverage | Status |
|-----|-------------|----------|--------|
| REQ-OM-01 | Atomic checkout (PENDIENTE, snapshot, stock, StockMovement, clear cart) | Unit + Web + IT | PASS |
| REQ-OM-02 | Empty cart rejection 400, no order created | Unit + Web + IT | PASS |
| REQ-OM-03 | Insufficient stock 422, full rollback (0 order rows) | Unit + Web + IT | PASS |
| REQ-OM-04 | Unit price snapshot (item.getUnitPrice(), never product.getPrice()) | OrderMapperTest + unit + IT | PASS |
| REQ-OM-05 | Client order history (own orders, date DESC) | Unit + IT | PASS |
| REQ-OM-06 | Client order detail + IDOR 404 (not 403) | Unit + Web + IT | PASS |
| REQ-OM-07 | Simulated payment PENDIENTE to CONFIRMADA | Unit + Web + IT | PASS |
| REQ-OM-08 | Invalid payment transition 422 (CONFIRMADA, CANCELADA) | Unit (both statuses) + Web + IT | PASS |
| REQ-OM-09 | IDOR on payment 404 | Unit + Web + IT | PASS |
| REQ-OM-10 | Admin paginated list + userId in response | Unit + Web + IT | PASS |
| REQ-OM-11 | Admin order detail (any user, 404 if missing) | Unit + Web + IT | PASS |
| REQ-OM-12 | Admin free-form status update, no guard | Unit + Web + IT | PASS |
| REQ-OM-13 | 401 unauthenticated, 403 CLIENTE on admin | Web (partial, filters off) + IT (full) | PASS |

---

## Design ADRs

| ADR | Decision | Implemented | Status |
|-----|----------|-------------|--------|
| ADR-1 | checkout @Transactional, two-pass validate-then-mutate | Pass A: lock+validate all; Pass B: save Order then per-item + clearCart | PASS |
| ADR-2 | findByIdWithLock with PESSIMISTIC_WRITE in ProductRepository | Present with @Lock(LockModeType.PESSIMISTIC_WRITE) @Query | PASS |
| ADR-3 | findByUserOrderByOrderDateDesc, findByIdAndUser, findByOrder | All three present | PASS |
| ADR-4 overridden | OrderResponse includes Long userId (orchestrator override) | Record: id, userId, orderDate, status, total, items | PASS |
| ADR-5 | Mapper reads item.getUnitPrice() for subtotal, order.getTotal() for total | Confirmed in OrderMapper and OrderMapperTest | PASS |
| ADR-6 | Two controllers; client uses @AuthenticationPrincipal, admin does not | OrderController with principal, AdminOrderController without | PASS |
| ADR-7 | EmptyCartException->400, OrderStatusTransitionException->422 | Both handlers in GlobalExceptionHandler | PASS |
| ADR-8 | pay(): findByIdAndUser (IDOR->404) + PENDIENTE-only guard (422) | Confirmed in OrderServiceImpl.pay | PASS |
| ADR-9 | updateStatus(): findById, no transition guard | Confirmed in OrderServiceImpl.updateStatus | PASS |
| ADR-10 | Unit (Mockito) + web slice (@WebMvcTest) + IT (Testcontainers) | 52 new tests across 5 classes | PASS |

---

## Issues

### CRITICAL

None.

### WARNING

**W-01 - 401 coverage absent from web slice**

OrderControllerTest and AdminOrderControllerTest both set addFilters=false, disabling the JWT filter.
No 401 assertion runs at the web slice layer. The file comments acknowledge this and defer to IT.
IT tests (unauthenticated_checkout_returns_401, unauthenticated_admin_list_returns_401) fully cover 401.
Accepted trade-off consistent with CartControllerTest; however, the HTTP contract for unauthenticated
access is not pinned at the web layer.

### SUGGESTION

**S-01 - N+1 not annotated in production code**

Design RISK-2 documents the N+1 in getAllOrders/getMyOrders (findByOrder per order in a loop), but
OrderServiceImpl has no inline comment. A TODO would make the trade-off explicit for future contributors.

**S-02 - IT rollback test uses manual stock reset, not true concurrency**

checkout_rollback test adds items at stock=5, then manually sets stock=0 before checkout.
This validates the validation path correctly but does not exercise the pessimistic-lock path
under actual concurrent access. Correct for scope; relevant if lock correctness is audited later.

---

## Task Completion

All 37 tasks in tasks.md are marked [x].

| Phase | Tasks | Status |
|-------|-------|--------|
| Phase 1 - Repository Queries | P1.1-P1.4 (4 tasks) | All complete |
| Phase 2 - DTOs, Enums, Mapper | P2.1-P2.8 (8 tasks) | All complete |
| Phase 3 - Exceptions and Handler | P3.1-P3.4 (4 tasks) | All complete |
| Phase 4 - Service Layer | P4.1-P4.11 (11 tasks) | All complete |
| Phase 5 - Controllers | P5.1-P5.8 (8 tasks) | All complete |
| Phase 6 - Integration Tests | P6.1-P6.17 (17 tasks) | All complete |
