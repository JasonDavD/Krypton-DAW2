# category-management Specification

## Purpose

Define public read access to categories and ADMIN-controlled CRUD for category
management. Enforces name uniqueness and protects referential integrity: a category
with associated products MUST NOT be deleted.

---

## Requirements

### Requirement: Public list categories

`GET /api/categories` MUST return all categories without authentication. Pagination
is OPTIONAL for this endpoint; returning all categories in a plain list is acceptable
given typical catalog cardinality. The response MUST use `CategoryResponse` DTOs;
the `Category` entity MUST NOT be exposed directly.

#### Scenario: List categories (unauthenticated)

- GIVEN categories "Electronics" and "Books" exist
- WHEN an unauthenticated client calls `GET /api/categories`
- THEN responds 200 with a list containing both categories as `CategoryResponse` objects

#### Scenario: Empty catalog returns empty list

- GIVEN no categories exist
- WHEN `GET /api/categories`
- THEN responds 200 with an empty list (not 404)

---

### Requirement: Public get category by id

`GET /api/categories/{id}` MUST return a single `CategoryResponse` for the requested
id. The endpoint MUST be accessible without authentication. If the category does not
exist, the system MUST respond 404.

#### Scenario: Get existing category

- GIVEN a category with id=3 and name="Sports" exists
- WHEN `GET /api/categories/3`
- THEN responds 200 with `CategoryResponse {id: 3, name: "Sports", description: ...}`

#### Scenario: Category not found

- GIVEN no category with id=999
- WHEN `GET /api/categories/999`
- THEN responds 404 with an `ApiError` body

---

### Requirement: ADMIN create category

`POST /api/admin/categories` MUST create a new category. The request MUST be
authenticated as ADMIN. The `name` field MUST be unique (case-insensitive comparison
is RECOMMENDED; the database enforces UNIQUE on `name`). Required fields: `name`.
Optional fields: `description`. On success, MUST respond 201 with the created
`CategoryResponse`.

#### Scenario: ADMIN creates category successfully

- GIVEN a valid ADMIN JWT and a `CategoryRequest` with name="Furniture" (not yet existing)
- WHEN `POST /api/admin/categories`
- THEN responds 201 with `CategoryResponse` containing the generated id and name="Furniture"

#### Scenario: Duplicate category name rejected

- GIVEN a category named "Electronics" already exists
- WHEN ADMIN attempts `POST /api/admin/categories` with name="Electronics"
- THEN responds 409 with `ApiError` indicating duplicate category name (DuplicateCategoryName)

#### Scenario: Non-ADMIN access denied

- GIVEN a valid JWT with role CLIENTE
- WHEN `POST /api/admin/categories`
- THEN responds 403

#### Scenario: Unauthenticated access denied

- GIVEN no Authorization header
- WHEN `POST /api/admin/categories`
- THEN responds 401

#### Scenario: Invalid input (blank name)

- GIVEN a `CategoryRequest` with name="" or name=null
- WHEN ADMIN calls `POST /api/admin/categories`
- THEN responds 400 with validation error details

---

### Requirement: ADMIN update category

`PUT /api/admin/categories/{id}` MUST update an existing category. The request MUST
be authenticated as ADMIN. If the new `name` conflicts with an existing category
(other than the one being updated), the system MUST respond 409. If the category does
not exist, MUST respond 404. On success, MUST respond 200 with the updated
`CategoryResponse`.

#### Scenario: ADMIN updates category successfully

- GIVEN a category with id=2 and name="Books" and ADMIN JWT
- WHEN `PUT /api/admin/categories/2` with `{"name": "Literature"}`
- THEN responds 200 with `CategoryResponse {name: "Literature"}`

#### Scenario: Name conflict on update

- GIVEN categories "Electronics" (id=1) and "Sports" (id=2)
- WHEN ADMIN calls `PUT /api/admin/categories/2` with `{"name": "Electronics"}`
- THEN responds 409 (DuplicateCategoryName)

#### Scenario: Category not found on update

- GIVEN no category with id=999
- WHEN `PUT /api/admin/categories/999`
- THEN responds 404

---

### Requirement: ADMIN delete category

`DELETE /api/admin/categories/{id}` MUST permanently remove the category from the
database IF AND ONLY IF no product currently references it via `category_id`. If any
product references the category, the system MUST reject the deletion with 409 Conflict
before touching the database. This guard MUST be enforced at the service layer (not
by catching a database FK violation). If the category does not exist, MUST respond 404.
On success (no products reference it), MUST respond 204.

The decision is 409 Conflict (not 422). The reason is that the conflict is a
referential integrity issue, not a semantic validation error.

Note: soft-deleted products (active=false) still reference the category via `category_id`
and MUST be counted by the guard. A category MUST NOT be deleted while any product —
active or soft-deleted — references it. This is an explicit design decision (engram #298).

#### Scenario: ADMIN deletes category with no products

- GIVEN category C1 exists and no products (active or inactive) reference C1
- WHEN ADMIN calls `DELETE /api/admin/categories/{C1.id}`
- THEN responds 204; the category row is removed from the database

#### Scenario: Delete rejected when products reference category

- GIVEN category C1 exists and at least one product has `category_id = C1.id`
- WHEN ADMIN calls `DELETE /api/admin/categories/{C1.id}`
- THEN responds 409 with an `ApiError` body; the category and all associated products MUST remain unchanged

#### Scenario: Guard is enforced at service layer (not DB exception)

- GIVEN a category referenced by products
- WHEN the delete is attempted
- THEN the service MUST count products by category BEFORE issuing any DELETE, and MUST throw the business exception if count > 0; a raw DataIntegrityViolationException MUST NOT propagate to the client

#### Scenario: Delete non-existent category

- GIVEN no category with id=999
- WHEN `DELETE /api/admin/categories/999`
- THEN responds 404

---

## Response Shape

```
CategoryResponse {
  id:          Long
  name:        String
  description: String (nullable)
}
```

The `Category` entity MUST NOT be returned directly from any controller or service
method that crosses the package boundary. All responses MUST be DTOs.
