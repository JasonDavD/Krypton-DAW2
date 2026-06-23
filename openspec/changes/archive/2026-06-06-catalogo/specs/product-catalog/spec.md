# product-catalog Specification

## Purpose

Define the public browsing surface for products (list, search, filter, paginate, get by id)
and the ADMIN management surface (create, update, delete). Stock is treated as a
read-only cached counter at this layer; catalog operations MUST NOT mutate it.

---

## Requirements

### Requirement: Public product listing (paginated, active only)

`GET /api/products` MUST return a paginated list of products where `active = TRUE`.
The response MUST be wrapped in a `PageResponse<ProductResponse>` DTO (content, pageNumber,
pageSize, totalElements, totalPages). The endpoint MUST be accessible without authentication.

Filters are all optional and composable:
- `name` — case-insensitive substring match (ILIKE `%value%`)
- `categoryId` — exact match on `category_id`
- `minPrice` / `maxPrice` — inclusive range on `price`

When no filters are provided, all active products MUST be returned.
Filters MUST be composed with logical AND when multiple are supplied.
The `active = TRUE` predicate MUST always be applied; it is not a client-controlled filter.

#### Scenario: List all active products (no filters)

- GIVEN the catalog contains products P1 (active=true) and P2 (active=false)
- WHEN an unauthenticated client calls `GET /api/products`
- THEN responds 200 with a `PageResponse` containing only P1; P2 MUST NOT appear

#### Scenario: Filter by name (case-insensitive substring)

- GIVEN active products "Laptop Pro" and "Mouse USB" exist
- WHEN `GET /api/products?name=laptop`
- THEN responds 200 with a `PageResponse` containing only "Laptop Pro"

#### Scenario: Filter by category

- GIVEN active products in category C1 and C2 exist
- WHEN `GET /api/products?categoryId=1`
- THEN responds 200 with only products belonging to C1

#### Scenario: Filter by price range

- GIVEN active products priced at 50, 150, and 300
- WHEN `GET /api/products?minPrice=100&maxPrice=200`
- THEN responds 200 with only the product priced at 150

#### Scenario: Combined filters

- GIVEN active products "Laptop Pro" in category C1 at price 1200 and "Laptop Mini" in C2 at price 600
- WHEN `GET /api/products?name=laptop&categoryId=1&minPrice=1000`
- THEN responds 200 with only "Laptop Pro"

#### Scenario: Pagination metadata present

- GIVEN 25 active products exist
- WHEN `GET /api/products?page=0&size=10`
- THEN responds 200 with `pageSize=10`, `totalElements=25`, `totalPages=3`, and 10 items in `content`

#### Scenario: Inactive products never appear

- GIVEN a product with `active = false` matching every possible filter criterion
- WHEN any public listing or search endpoint is called with any filter combination
- THEN the inactive product MUST NOT appear in any response

---

### Requirement: Public get product by id

`GET /api/products/{id}` MUST return a single `ProductResponse` for an active product.
The endpoint MUST be accessible without authentication.
If the product does not exist OR exists with `active = false`, the endpoint MUST respond 404.

#### Scenario: Get active product by id

- GIVEN an active product with id=42 exists
- WHEN an unauthenticated client calls `GET /api/products/42`
- THEN responds 200 with the `ProductResponse` for that product (including stock as read-only field)

#### Scenario: Product not found

- GIVEN no product with id=999 exists
- WHEN `GET /api/products/999`
- THEN responds 404 with an `ApiError` body

#### Scenario: Inactive product treated as not found

- GIVEN a product with id=7 exists but `active = false`
- WHEN `GET /api/products/7`
- THEN responds 404 (inactive products MUST NOT be exposed via public GET by id)

---

### Requirement: ADMIN create product

`POST /api/admin/products` MUST create a new product. The request MUST be authenticated
as a user with role ADMIN. The `sku` field MUST be unique across all products.
Required fields: `sku`, `name`, `price`, `categoryId`.
Optional fields: `description`, `imageUrl`, `stock` (bootstrap value, default 0), `active` (default true).

The `stock` field in the request is a one-time bootstrap value written directly to
`products.stock`. It MUST NOT create a `stock_movement` record. This is an explicit
bootstrap write, not an inventory event.

The referenced `categoryId` MUST exist; if not, the system MUST respond 404 (category not found).

On success, the system MUST respond 201 with the created `ProductResponse`.

#### Scenario: ADMIN creates product successfully

- GIVEN a valid ADMIN JWT and a `ProductRequest` with unique sku="SKU-001", name="Laptop", price=1200.00, categoryId=(existing)
- WHEN `POST /api/admin/products` with `Authorization: Bearer <admin-token>`
- THEN responds 201 with `ProductResponse` containing the generated id, sku, name, price, and stock=0

#### Scenario: ADMIN creates product with explicit initial stock

- GIVEN a valid ADMIN JWT and a `ProductRequest` with stock=50
- WHEN `POST /api/admin/products`
- THEN responds 201 with `ProductResponse.stock = 50`; no `stock_movement` record is created

#### Scenario: Duplicate SKU rejected

- GIVEN a product with sku="SKU-001" already exists
- WHEN ADMIN attempts `POST /api/admin/products` with sku="SKU-001"
- THEN responds 409 with `ApiError` indicating duplicate SKU (DuplicateSku)

#### Scenario: Non-ADMIN access denied

- GIVEN a valid JWT with role CLIENTE
- WHEN `POST /api/admin/products`
- THEN responds 403

#### Scenario: Unauthenticated access denied

- GIVEN no Authorization header
- WHEN `POST /api/admin/products`
- THEN responds 401

#### Scenario: Invalid input (missing required fields)

- GIVEN a `ProductRequest` missing `name` or with `price = null`
- WHEN ADMIN calls `POST /api/admin/products`
- THEN responds 400 with validation error details

#### Scenario: Category not found

- GIVEN a valid ADMIN JWT and a `ProductRequest` with categoryId=9999 (non-existent)
- WHEN `POST /api/admin/products`
- THEN responds 404 (category does not exist)

---

### Requirement: ADMIN update product

`PUT /api/admin/products/{id}` MUST update an existing product's fields. The request
MUST be authenticated as ADMIN. If a new `sku` is provided and it conflicts with
another product, the system MUST respond 409. If the product does not exist, MUST
respond 404. On success, MUST respond 200 with the updated `ProductResponse`.

The `stock` field MUST NOT be updated via this endpoint. If `stock` is included in
the request body, it MUST be silently ignored. Stock mutations belong to the inventory
layer.

#### Scenario: ADMIN updates product successfully

- GIVEN an existing product with id=10 and ADMIN JWT
- WHEN `PUT /api/admin/products/10` with `{"name": "Laptop Pro v2", "price": 1350.00}`
- THEN responds 200 with the updated `ProductResponse`; stock value is unchanged

#### Scenario: SKU conflict on update

- GIVEN product A with sku="SKU-A" and product B with sku="SKU-B"
- WHEN ADMIN calls `PUT /api/admin/products/{B.id}` with sku="SKU-A"
- THEN responds 409 (DuplicateSku)

#### Scenario: Product not found on update

- GIVEN no product with id=999
- WHEN `PUT /api/admin/products/999`
- THEN responds 404

#### Scenario: Stock ignored on update

- GIVEN an active product with stock=10
- WHEN ADMIN calls `PUT /api/admin/products/{id}` with body including `"stock": 999`
- THEN responds 200; stock in the response and database MUST still be 10

---

### Requirement: ADMIN delete product

`DELETE /api/admin/products/{id}` MUST mark the product as `active = false` (soft
delete). The product MUST NOT be physically removed from the database. On success,
MUST respond 204. If the product does not exist, MUST respond 404.

#### Scenario: ADMIN soft-deletes product

- GIVEN an active product with id=5 and ADMIN JWT
- WHEN `DELETE /api/admin/products/5`
- THEN responds 204; the product row still exists in `products` with `active = false`

#### Scenario: Deleted product no longer visible publicly

- GIVEN a product that was soft-deleted (active=false)
- WHEN `GET /api/products` or `GET /api/products/{id}`
- THEN the product MUST NOT appear (404 or absent from list)

#### Scenario: Delete non-existent product

- GIVEN no product with id=999
- WHEN `DELETE /api/admin/products/999`
- THEN responds 404

---

### Requirement: Stock is read-only in catalog

The `stock` field MUST be exposed in `ProductResponse` as a read-only integer.
Catalog service operations (create, update, delete) MUST NOT write to
`stock_movement`. The `ProductService` MUST NOT inject or call any stock or inventory
service. The only catalog-layer write to `products.stock` is the one-time bootstrap
value accepted at product creation.

This MUST be documented as an explicit architectural boundary comment in the service
implementation.

#### Scenario: Stock exposed in response

- GIVEN a product with stock=15
- WHEN the product appears in any public or admin response
- THEN the response MUST include `"stock": 15` as a read-only field

#### Scenario: Catalog update does not mutate stock

- GIVEN a product with stock=15
- WHEN ADMIN calls `PUT /api/admin/products/{id}` with any payload (with or without stock field)
- THEN stock MUST remain 15 in the database; no `stock_movement` record MUST exist

---

## Response Shape

```
ProductResponse {
  id:          Long
  sku:         String
  name:        String
  description: String (nullable)
  price:       BigDecimal
  stock:       Integer        // read-only; catalog never mutates
  imageUrl:    String (nullable)
  active:      Boolean
  categoryId:  Long
}

PageResponse<T> {
  content:       List<T>
  pageNumber:    Integer
  pageSize:      Integer
  totalElements: Long
  totalPages:    Integer
}
```

Entities (`Product`, `Category`) MUST NOT be returned directly from any controller or
service method that crosses the package boundary. All responses MUST be DTOs.
