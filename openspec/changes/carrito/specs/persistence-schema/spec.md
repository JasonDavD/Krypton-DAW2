# Delta for persistence-schema

## ADDED Requirements

### Requirement: Unique Constraint on cart_item (cart_id, product_id)

The `cart_item` table MUST have a UNIQUE constraint on `(cart_id, product_id)`.
This constraint MUST be introduced by Flyway migration `V4__add_cart_item_unique.sql`
as an additive change to the existing schema. The migration MUST NOT modify or
re-create any previously applied migration file.

#### Scenario: Duplicate cart item rejected at DB level

- GIVEN a `cart_item` row exists with `cart_id = 1` and `product_id = 5`
- WHEN another row is inserted with the same `(cart_id, product_id)` pair
- THEN the database rejects the insertion with a UNIQUE constraint violation

#### Scenario: V4 migration applied cleanly

- GIVEN a database with V1–V3 already applied
- WHEN Flyway applies `V4__add_cart_item_unique.sql`
- THEN the constraint `UNIQUE(cart_id, product_id)` exists on `cart_item`
- AND no existing rows are affected or removed

#### Scenario: Different cart, same product — allowed

- GIVEN `cart_item` rows exist for `(cart_id=1, product_id=5)` and `(cart_id=2, product_id=5)`
- WHEN no new insertion is attempted
- THEN both rows coexist without constraint violation

### Requirement: cart.updated_at Kept Current by Service Layer

The `cart.updated_at` column MUST be updated to the current timestamp on every
write operation (add item, update item, remove item, clear cart).
This MUST be managed explicitly by the service layer — no DB trigger or JPA
lifecycle callback is used for this purpose.

#### Scenario: updated_at advances after add

- GIVEN a cart with `updated_at = T0`
- WHEN an item is added via the service
- THEN `cart.updated_at` is a timestamp >= T0 (strictly after T0 in practice)

#### Scenario: updated_at advances after clear

- GIVEN a cart with `updated_at = T0`
- WHEN all items are cleared via the service
- THEN `cart.updated_at` is updated to a timestamp >= T0

#### Scenario: GET does not mutate updated_at

- GIVEN a cart with `updated_at = T0`
- WHEN `GET /api/cart` is called
- THEN `cart.updated_at` remains T0 (read-only operations do not touch it)
