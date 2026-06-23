# Order Management Specification

## Purpose

Covers the complete purchase flow for Krypton E-commerce: atomic checkout (cart → persistent order
with price snapshot, stock decrement, and stock movement), client order lifecycle (history + detail
with IDOR protection), simulated payment (PENDIENTE → CONFIRMADA), and admin order administration
(paginated listing, detail, free-form status update). All endpoints require authentication; admin
endpoints require ROLE_ADMIN.

---

## Requirements

### Requirement: REQ-OM-01 — Atomic Checkout

The system MUST expose `POST /api/orders/checkout` for authenticated clients. Within a single
`@Transactional` boundary it MUST: validate all cart items have sufficient stock; create an `Order`
with `status=PENDIENTE` and `total=Σ(quantity × product.price)`; create one `OrderItem` per cart
line with a **price snapshot** (`unit_price = product.price` at checkout time); decrement each
product's stock; insert one `SALIDA` `stock_movement` per item; and clear the cart. On success it
MUST return `201 OrderResponse`.

#### Scenario: Successful checkout

- GIVEN an authenticated client with a non-empty cart where all items have sufficient stock
- WHEN the client calls `POST /api/orders/checkout`
- THEN the system creates one `Order` with `status=PENDIENTE`
- AND creates one `OrderItem` per cart line with `unit_price` equal to `product.price` at that moment
- AND decrements each product's stock by the ordered quantity
- AND inserts one `SALIDA` `stock_movement` per item with `reference="ORDER-{orderId}"`
- AND clears the cart so it contains zero items
- AND returns `201` with an `OrderResponse` containing all items and the computed `total`

#### Scenario: Price snapshot isolation

- GIVEN an order created with `unit_price=100.00` for a product
- WHEN the product's price is later updated to `150.00`
- THEN the existing `order_items.unit_price` remains `100.00` and is unaffected by the price change

---

### Requirement: REQ-OM-02 — Empty Cart Rejection

The system MUST reject a checkout request when the authenticated client's cart contains zero items,
returning `400 EmptyCartException`. No order MUST be created.

#### Scenario: Checkout with empty cart

- GIVEN an authenticated client whose cart is empty
- WHEN the client calls `POST /api/orders/checkout`
- THEN the system returns `400` with an error body identifying `EmptyCartException`
- AND no `Order` row is persisted in the database

---

### Requirement: REQ-OM-03 — Insufficient Stock Rollback

The system MUST reject the entire checkout and perform a full rollback when any single cart item's
requested quantity exceeds the product's available stock, returning `422 InsufficientStockException`.
No order, no order items, and no stock decrements MUST be persisted.

#### Scenario: One item exceeds stock

- GIVEN an authenticated client with a cart containing two items where the second item's quantity exceeds available stock
- WHEN the client calls `POST /api/orders/checkout`
- THEN the system returns `422` with an error body identifying `InsufficientStockException`
- AND zero `Order` rows are created
- AND the stock of all products remains unchanged (full rollback)

---

### Requirement: REQ-OM-04 — Unit Price Snapshot

The system MUST record `unit_price` in `order_items` equal to `product.price` at the moment of
checkout. Subsequent price changes to the product MUST NOT alter existing `order_items.unit_price`.

*(Covered by scenario "Price snapshot isolation" under REQ-OM-01.)*

---

### Requirement: REQ-OM-05 — Client Order History

The system MUST expose `GET /api/orders` for authenticated clients, returning a list of only that
client's own orders ordered by `order_date DESC`.

#### Scenario: Client retrieves own order list

- GIVEN an authenticated client who has placed two orders
- WHEN the client calls `GET /api/orders`
- THEN the system returns `200` with a list of exactly those two orders, newest first
- AND no orders belonging to other clients are included

#### Scenario: Client with no orders

- GIVEN an authenticated client who has never placed an order
- WHEN the client calls `GET /api/orders`
- THEN the system returns `200` with an empty list

---

### Requirement: REQ-OM-06 — Client Order Detail with IDOR Protection

The system MUST expose `GET /api/orders/{id}` for authenticated clients. It MUST return `200
OrderResponse` when the order exists and belongs to the requester. It MUST return `404` when the
order does not exist OR belongs to a different user (IDOR protection — no `403` leak).

#### Scenario: Client retrieves own order detail

- GIVEN an authenticated client who owns order with id=5
- WHEN the client calls `GET /api/orders/5`
- THEN the system returns `200 OrderResponse` with `id=5` and full item list

#### Scenario: IDOR — order belongs to another user

- GIVEN an authenticated client A and an order with id=9 owned by client B
- WHEN client A calls `GET /api/orders/9`
- THEN the system returns `404` (not `403`) so the existence of the order is not leaked

#### Scenario: Order does not exist

- GIVEN an authenticated client and a non-existent order id=999
- WHEN the client calls `GET /api/orders/999`
- THEN the system returns `404`

---

### Requirement: REQ-OM-07 — Simulated Payment

The system MUST expose `POST /api/orders/{id}/pay` for authenticated clients. The request body
MUST contain `{ "method": "CREDIT_CARD"|"YAPE"|"EFECTIVO" }`. When the order is in `PENDIENTE`
status and belongs to the requester, the system MUST transition it to `CONFIRMADA` and return
`200 OrderResponse`. No external payment provider is called.

#### Scenario: Successful payment

- GIVEN an authenticated client who owns order id=3 with `status=PENDIENTE`
- WHEN the client calls `POST /api/orders/3/pay` with body `{ "method": "YAPE" }`
- THEN the system transitions the order to `status=CONFIRMADA`
- AND returns `200 OrderResponse` with `status=CONFIRMADA`

---

### Requirement: REQ-OM-08 — Invalid Payment Transition

The system MUST reject payment attempts on orders that are not in `PENDIENTE` status, returning
`422 OrderStatusTransitionException`.

#### Scenario: Pay an already CONFIRMADA order

- GIVEN an authenticated client who owns order id=4 with `status=CONFIRMADA`
- WHEN the client calls `POST /api/orders/4/pay`
- THEN the system returns `422` with an error body identifying `OrderStatusTransitionException`
- AND the order status remains `CONFIRMADA`

#### Scenario: Pay a CANCELADA order

- GIVEN an authenticated client who owns order id=7 with `status=CANCELADA`
- WHEN the client calls `POST /api/orders/7/pay`
- THEN the system returns `422` with an error body identifying `OrderStatusTransitionException`

---

### Requirement: REQ-OM-09 — IDOR on Payment

The system MUST prevent a client from paying an order that belongs to another user. It MUST return
`404` (not `403`) when the order does not belong to the requester.

#### Scenario: Client pays another user's order

- GIVEN authenticated client A and order id=8 owned by client B
- WHEN client A calls `POST /api/orders/8/pay`
- THEN the system returns `404`
- AND the order status is unchanged

---

### Requirement: REQ-OM-10 — Admin Paginated Order List

The system MUST expose `GET /api/admin/orders` requiring `ROLE_ADMIN`, returning a paginated
`PageResponse<OrderResponse>` of ALL orders in the system. Query params `page`, `size`, and `sort`
MUST be supported.

#### Scenario: Admin retrieves all orders

- GIVEN an authenticated admin and three orders belonging to two different clients
- WHEN the admin calls `GET /api/admin/orders?page=0&size=10`
- THEN the system returns `200` with `PageResponse` containing all three orders

#### Scenario: Client role rejected on admin endpoint

- GIVEN an authenticated client (ROLE_CLIENTE)
- WHEN the client calls `GET /api/admin/orders`
- THEN the system returns `403`

---

### Requirement: REQ-OM-11 — Admin Order Detail

The system MUST expose `GET /api/admin/orders/{id}` requiring `ROLE_ADMIN`. It MUST return `200
OrderResponse` for any existing order regardless of owner. It MUST return `404` when the order
does not exist.

#### Scenario: Admin retrieves any order

- GIVEN an authenticated admin and an order id=10 owned by any client
- WHEN the admin calls `GET /api/admin/orders/10`
- THEN the system returns `200 OrderResponse` with `id=10`

#### Scenario: Order not found

- GIVEN an authenticated admin and a non-existent order id=999
- WHEN the admin calls `GET /api/admin/orders/999`
- THEN the system returns `404`

---

### Requirement: REQ-OM-12 — Admin Free-Form Status Update

The system MUST expose `PUT /api/admin/orders/{id}/status` requiring `ROLE_ADMIN`. The request body
MUST contain `{ "status": "PENDIENTE"|"CONFIRMADA"|"CANCELADA" }`. The admin MUST be able to
transition to any status with no guard on the transition. On success it MUST return `200
OrderResponse`.

#### Scenario: Admin cancels a CONFIRMADA order

- GIVEN an authenticated admin and order id=2 with `status=CONFIRMADA`
- WHEN the admin calls `PUT /api/admin/orders/2/status` with body `{ "status": "CANCELADA" }`
- THEN the system transitions the order to `status=CANCELADA`
- AND returns `200 OrderResponse` with `status=CANCELADA`

#### Scenario: Admin resets a CANCELADA order to PENDIENTE

- GIVEN an authenticated admin and order id=6 with `status=CANCELADA`
- WHEN the admin calls `PUT /api/admin/orders/6/status` with body `{ "status": "PENDIENTE" }`
- THEN the system transitions the order to `status=PENDIENTE`
- AND returns `200 OrderResponse` with `status=PENDIENTE`

---

### Requirement: REQ-OM-13 — Authentication and Authorization

All endpoints under `/api/orders/**` and `/api/admin/orders/**` MUST require a valid JWT token.
Unauthenticated requests MUST receive `401`. Requests from a client role (`ROLE_CLIENTE`) targeting
any `/api/admin/**` endpoint MUST receive `403`. A client MUST NOT be able to change order status
directly (no status-change endpoint exists for the client role).

#### Scenario: Unauthenticated checkout

- GIVEN no JWT token in the request
- WHEN a caller sends `POST /api/orders/checkout`
- THEN the system returns `401`

#### Scenario: Unauthenticated admin list

- GIVEN no JWT token in the request
- WHEN a caller sends `GET /api/admin/orders`
- THEN the system returns `401`

#### Scenario: Client accessing admin endpoint

- GIVEN an authenticated client with `ROLE_CLIENTE`
- WHEN the client calls `GET /api/admin/orders`
- THEN the system returns `403`

---

## OrderResponse Shape

```json
{
  "id": 1,
  "orderDate": "2026-06-07T14:30:00",
  "status": "PENDIENTE",
  "total": 5999.80,
  "items": [
    {
      "id": 1,
      "productId": 12,
      "productName": "Notebook",
      "quantity": 2,
      "unitPrice": 2999.90,
      "subtotal": 5999.80
    }
  ]
}
```

`subtotal` MUST equal `quantity × unitPrice` for each item. `total` MUST equal the sum of all
item subtotals. The entity (`Order`, `OrderItem`) MUST NOT be exposed directly; only DTOs are
returned.
