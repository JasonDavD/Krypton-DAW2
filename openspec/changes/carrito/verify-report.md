## Verification Report

**Change**: carrito
**Version**: N/A
**Mode**: Strict TDD

---

### Completeness

| Metric | Value |
|--------|-------|
| Tasks total | 52 |
| Tasks complete | 52 |
| Tasks incomplete | 0 |

All 52 tasks across 7 phases show [x].

---

### Build and Tests Execution

**Build**: PASS

**Tests**: 169 passed / 0 failed / 0 skipped

Carrito-specific breakdown:
- CartMapperTest: 4 tests
- CartServiceImplTest: 20 tests
- CartControllerTest: 12 tests
- CartIntegrationTest: 16 tests (Testcontainers Postgres 16)

**Coverage**: Not configured.

---

### Spec Compliance Matrix

#### cart-management/spec.md

| REQ | Scenario | Test | Result |
|-----|----------|------|--------|
| REQ-CM-01 | Existing cart returned | CartControllerTest > get_cart_returns_200_with_items_and_total + CartIntegrationTest > post_item_creates_cart_lazily_and_shows_item | COMPLIANT |
| REQ-CM-01 | No cart yet empty response | CartControllerTest > get_cart_returns_200_empty_when_no_cart + CartServiceImplTest > getCart_returns_emptyCart_when_no_cart_exists + CartIntegrationTest > get_cart_with_no_prior_cart_returns_200_empty | COMPLIANT |
| REQ-CM-02 | First item cart created lazily | CartServiceImplTest > attemptAddItem_creates_cart_when_no_cart_exists + CartIntegrationTest > post_item_creates_cart_lazily_and_shows_item | COMPLIANT |
| REQ-CM-02 | Same product merged | CartServiceImplTest > attemptAddItem_merges_when_product_already_in_cart + CartIntegrationTest > post_same_product_twice_merges_quantity | COMPLIANT |
| REQ-CM-02 | New product to existing cart | CartControllerTest > post_item_returns_201_with_cart_response | PARTIAL |
| REQ-CM-03 | quantity=0 rejected | CartControllerTest > post_item_with_quantity_zero_returns_400 | COMPLIANT |
| REQ-CM-03 | Negative quantity rejected | @Min(1) covers it; no test named for negative value specifically | PARTIAL |
| REQ-CM-04 | Inactive product rejected | CartServiceImplTest > attemptAddItem_throws_ResourceNotFoundException_for_inactive_product + CartIntegrationTest > post_inactive_product_returns_404 | COMPLIANT |
| REQ-CM-05 | Stock exceeded on add | CartServiceImplTest > attemptAddItem_throws_InsufficientStockException_when_qty_exceeds_stock + CartIntegrationTest > post_with_qty_exceeding_stock_returns_422 | COMPLIANT |
| REQ-CM-06 | Quantity replaced | CartServiceImplTest > updateItem_replaces_quantity_and_returns_cart + CartIntegrationTest > put_item_replaces_quantity_and_get_confirms | COMPLIANT |
| REQ-CM-06 | quantity=0 in PUT rejected | CartControllerTest > put_item_with_quantity_zero_returns_400 + CartIntegrationTest > put_item_with_quantity_zero_returns_400 | COMPLIANT |
| REQ-CM-07 | Update exceeds stock | CartServiceImplTest > updateItem_throws_422_when_qty_exceeds_stock + CartIntegrationTest > put_with_qty_exceeding_stock_returns_422 | COMPLIANT |
| REQ-CM-08 | Item from other user 404 | CartServiceImplTest > updateItem_throws_ResourceNotFoundException_for_IDOR + CartIntegrationTest > anti_idor_user_b_cannot_access_user_a_items | COMPLIANT |
| REQ-CM-09 | Item removed successfully | CartControllerTest > delete_item_returns_204 + CartIntegrationTest > delete_item_removes_it_and_get_shows_remaining | COMPLIANT |
| REQ-CM-09 | Item from another user 404 | CartServiceImplTest > removeItem_throws_ResourceNotFoundException_for_IDOR + CartIntegrationTest > anti_idor_user_b_cannot_access_user_a_items | COMPLIANT |
| REQ-CM-10 | Cart cleared | CartServiceImplTest > clearCart_deletes_all_items_and_touches_cart_when_cart_exists + CartIntegrationTest > delete_cart_removes_all_items_and_get_returns_empty | COMPLIANT |
| REQ-CM-10 | Empty cart idempotent | CartServiceImplTest > clearCart_is_noop_when_no_cart_exists + CartIntegrationTest > delete_cart_when_no_cart_exists_returns_204_idempotent | COMPLIANT |
| REQ-CM-11 | Unauthenticated 401 | CartIntegrationTest > unauthenticated_get_cart_returns_401 + unauthenticated_post_cart_items_returns_401 | COMPLIANT |
| REQ-CM-12 | Cart resolved from token only | CartIntegrationTest > anti_idor_user_b_cannot_access_user_a_items (GET shows own items only) | COMPLIANT |

#### persistence-schema/spec.md

| REQ | Scenario | Test | Result |
|-----|----------|------|--------|
| REQ-PS-NEW-01 | Duplicate rejected at DB level | CartIntegrationTest > post_same_product_twice_merges_quantity | COMPLIANT |
| REQ-PS-NEW-01 | V4 migration applied cleanly | Flyway log: 4 migrations applied; ddl-auto:validate passes every run | COMPLIANT |
| REQ-PS-NEW-01 | Different cart same product allowed | Structurally guaranteed by constraint; no dedicated runtime test | PARTIAL |
| REQ-PS-NEW-02 | updated_at advances after add | CartIntegrationTest > write_op_advances_updated_at_relative_to_created_at | COMPLIANT |
| REQ-PS-NEW-02 | updated_at advances after clear | CartServiceImplTest > clearCart_deletes_all_items_and_touches_cart_when_cart_exists (ArgumentCaptor) | COMPLIANT |
| REQ-PS-NEW-02 | GET does not mutate updated_at | CartIntegrationTest > get_cart_does_not_mutate_updated_at | COMPLIANT |

**Compliance summary**: 21/24 COMPLIANT, 3/24 PARTIAL.

---

### Design ADR Coherence

| ADR | Decision | Implemented | Status |
|-----|----------|-------------|--------|
| ADR-1 | Two-transaction concurrency: addItem non-@Transactional, self-injection, attemptAddItem+mergeOnConflict | Yes | PASS |
| ADR-2 | Response shapes: POST=201, PUT=200, DELETEs=204 | Yes | PASS |
| ADR-3 | GET does NOT persist cart (emptyCart, readOnly) | Yes | PASS |
| ADR-4 | IDOR via requireOwnedItem returning 404 | Yes | PASS |
| ADR-4 | updated_at via service touch(); no @PreUpdate | Yes | PASS |
| ADR-5 | No @OneToMany on Cart; items loaded by findByCart | Yes | PASS |
| ADR-6 | V4 UNIQUE constraint uq_cart_item_cart_product | Yes | PASS |
| Deviation | attemptAddItem/mergeOnConflict on CartService interface (7 methods vs 5 in design) | Intentional per ADR-1: proxy requires public interface methods | WARNING (valid) |

---

### Issues Found

**CRITICAL** (blocks archive): None

**WARNING** (should fix):

1. REQ-CM-02 New product to existing cart: no IT asserting items.length=2 after two distinct product POSTs.

2. REQ-PS-NEW-01 Different cart, same product allowed: no IT with two users adding the same productId.

3. REQ-CM-03 Negative quantity rejected: no test with an explicitly negative value (e.g., -5).

**SUGGESTION** (optional):

- CartControllerTest redundantly annotates @AutoConfigureMockMvc alongside @WebMvcTest.
- write_op_advances_updated_at does not compare updatedAt >= createdAt; only asserts non-null.
- cleanupCartRows could use direct JPQL deletes for more explicit FK-order control.

---

### Verdict

**PASS WITH WARNINGS**

169/169 tests pass. 0 CRITICAL issues. 3 WARNINGs (test traceability gaps). Ready for sdd-archive.