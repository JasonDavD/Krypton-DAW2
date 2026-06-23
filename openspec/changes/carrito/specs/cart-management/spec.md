# Cart Management Specification

## Purpose

Define the behavior of the authenticated user's persistent cart: lazy creation,
add/merge, update, remove, and clear operations, with stock validation and strict
owner isolation (anti-IDOR).

## Requirements

### Requirement: Retrieve Cart

The system MUST return the authenticated user's cart on `GET /api/cart`.
If no cart exists yet, the system MUST return a 200 response with an empty items
list — it MUST NOT return 404 and MUST NOT create the cart.

#### Scenario: Existing cart returned

- GIVEN an authenticated user who has a cart with items
- WHEN they call `GET /api/cart`
- THEN the response is 200 with a `CartResponse` containing all their items

#### Scenario: No cart yet — empty response

- GIVEN an authenticated user who has never added items
- WHEN they call `GET /api/cart`
- THEN the response is 200 with `items: []`, `totalItems: 0`, `totalPrice: 0`

### Requirement: Add Item (with Lazy Cart Creation and Merge)

The system MUST accept `POST /api/cart/items` with `{ productId, quantity >= 1 }`.
If the user has no cart, the system MUST create one before adding the item.
If the product is already in the cart, the system MUST add the quantities (upsert —
no duplicate rows, no rejection). The response MUST be 201 with the updated
`CartResponse`.

#### Scenario: First item — cart created lazily

- GIVEN an authenticated user with no existing cart
- WHEN they POST `{ productId: 1, quantity: 2 }` to `/api/cart/items`
- THEN a cart is created, the item is added, and the response is 201 with the item

#### Scenario: Same product added again — quantities merged

- GIVEN the user's cart already contains product 1 with quantity 2
- WHEN they POST `{ productId: 1, quantity: 3 }` to `/api/cart/items`
- THEN the cart item shows quantity 5 (not a duplicate row) and the response is 201

#### Scenario: New product added to existing cart

- GIVEN the user's cart already contains product 1
- WHEN they POST `{ productId: 2, quantity: 1 }` to `/api/cart/items`
- THEN the cart now has two distinct items and the response is 201

### Requirement: Add Item Validation

The system MUST reject `POST /api/cart/items` when `quantity` is less than 1
with a 400 response. The system MUST NOT create or mutate the cart in that case.

#### Scenario: quantity = 0 rejected

- GIVEN an authenticated user
- WHEN they POST `{ productId: 1, quantity: 0 }` to `/api/cart/items`
- THEN the response is 400 and the cart is unchanged

#### Scenario: Negative quantity rejected

- GIVEN an authenticated user
- WHEN they POST `{ productId: 1, quantity: -5 }` to `/api/cart/items`
- THEN the response is 400

### Requirement: Add Item — Inactive Product

The system MUST return 404 (`ResourceNotFoundException`) when the requested
product does not exist or has `active = false`.

#### Scenario: Inactive product rejected

- GIVEN a product exists with `active = false`
- WHEN an authenticated user POSTs that productId to `/api/cart/items`
- THEN the response is 404

### Requirement: Add Item — Insufficient Stock

The system MUST return 422 (`InsufficientStockException`) when the resulting
quantity (existing cart quantity + requested quantity) exceeds `product.stock`.
Stock is checked only — never reserved or decremented.

#### Scenario: Stock exceeded on add

- GIVEN product 1 has `stock = 5` and the user's cart already has 3 units
- WHEN the user POSTs `{ productId: 1, quantity: 3 }` (total would be 6)
- THEN the response is 422 and the cart item quantity stays at 3

### Requirement: Update Item Quantity

The system MUST accept `PUT /api/cart/items/{itemId}` with `{ quantity >= 1 }`.
The quantity MUST replace (not add to) the existing quantity.
The system MUST return 200 with the updated `CartResponse`.

#### Scenario: Quantity replaced

- GIVEN the user's cart has item 10 with quantity 2
- WHEN they PUT `{ quantity: 7 }` to `/api/cart/items/10`
- THEN the item now has quantity 7 and the response is 200

#### Scenario: quantity = 0 in PUT rejected

- GIVEN the user's cart has item 10
- WHEN they PUT `{ quantity: 0 }` to `/api/cart/items/10`
- THEN the response is 400 (use DELETE to remove an item)

### Requirement: Update Item — Insufficient Stock

The system MUST return 422 when the updated quantity exceeds `product.stock`.

#### Scenario: Update exceeds stock

- GIVEN product 1 has `stock = 4`
- WHEN the user PUTs `{ quantity: 5 }` to the item referencing product 1
- THEN the response is 422 and the quantity is unchanged

### Requirement: Update Item — Owner Isolation (Anti-IDOR)

The system MUST verify that `{itemId}` belongs to the authenticated user's cart.
If the item does not exist or belongs to a different user's cart, the system MUST
return 404 — never expose that the item exists.

#### Scenario: Item belonging to another user returns 404

- GIVEN user A has cart item 99
- WHEN user B calls `PUT /api/cart/items/99`
- THEN the response is 404 (user B cannot see or mutate user A's items)

### Requirement: Remove Single Item

The system MUST accept `DELETE /api/cart/items/{itemId}` and remove that item
from the authenticated user's cart, returning 204.
If the item does not exist or belongs to another user, the system MUST return 404.

#### Scenario: Item removed successfully

- GIVEN the user's cart has item 10
- WHEN they call `DELETE /api/cart/items/10`
- THEN the item is gone and the response is 204

#### Scenario: Item from another user returns 404

- GIVEN user A owns item 99
- WHEN user B calls `DELETE /api/cart/items/99`
- THEN the response is 404

### Requirement: Clear Cart

The system MUST accept `DELETE /api/cart` and remove all items from the
authenticated user's cart, returning 204.
If the cart is empty or does not exist, the system MUST still return 204
(idempotent — no error on repeated calls).

#### Scenario: Cart cleared

- GIVEN the user's cart has 3 items
- WHEN they call `DELETE /api/cart`
- THEN all items are removed and the response is 204

#### Scenario: Empty cart cleared idempotently

- GIVEN the user has no cart (or an empty cart)
- WHEN they call `DELETE /api/cart`
- THEN the response is 204 with no error

### Requirement: Authentication Required

All `/api/cart` endpoints MUST require a valid JWT. Requests without a token or
with an invalid/expired token MUST receive 401.

#### Scenario: Unauthenticated request rejected

- GIVEN no Authorization header is provided
- WHEN any `/api/cart` endpoint is called
- THEN the response is 401

### Requirement: Cart Identity via Token

The cart MUST be identified from the authenticated user's token. No `cartId`
MUST appear in any URL. All isolation MUST be enforced server-side by resolving
`principal.username → User → Cart`.

#### Scenario: Cart resolved from token only

- GIVEN two users with distinct carts
- WHEN each calls `GET /api/cart` with their own token
- THEN each receives only their own cart data with no cross-user leakage
