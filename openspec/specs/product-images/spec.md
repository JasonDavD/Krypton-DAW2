# Product Images Specification

> Delta spec for `product-images`. Phase: SDD SPEC.
> Artifact store: hybrid (engram `sdd/product-images/spec` + this file).
> Source: proposal `sdd/product-images/proposal` (#382). Date: 2026-06-17.

## Purpose

Defines what MUST be true after `product-images` is applied. Describes observable
behaviour and contracts — not implementation. Backend layered architecture
(`pe.com.krypton`): controller → service(+impl) → repository → model;
never expose `@Entity` — DTOs at the boundary.

**TS types referenced**: `ProductImageResponse`, `ProductResponse` — defined in
`frontend/src/app/models/product.model.ts`.

---

## Out of Scope (explicit)

- Product `description` field (already exists and is rendered — not this change).
- Object storage (S3/MinIO): filesystem only (academic scope).
- Image resizing, thumbnails, CDN.
- Batch multi-file upload.
- Physical file cleanup on product soft-delete (orphans are accepted/documented).

---

## Capability: product-images

---

### Requirement: Image Upload

An admin MUST be able to upload one image per request to a product via multipart
form-data. The first image uploaded to a product becomes the cover automatically.
Subsequent uploads are added at the end of the display order.

**Endpoint**: `POST /api/admin/products/{id}/images` — ROLE_ADMIN required.

**Validation rules** (app-layer only — no DB constraint):
- Content-type allowlist: `image/jpeg`, `image/png`, `image/webp`.
- Maximum file size: 5 MB.
- Maximum images per product: 10 (`MAX_IMAGES_PER_PRODUCT = 10`).

**On success**: one `product_image` row is created; the binary is stored on the local
filesystem with a UUID-generated filename; if this is the first image for the product
`products.image_url` is set to `{base-url}/api/uploads/images/{uuid-filename}` within
the same `@Transactional` boundary.

#### Scenario: Successful upload — first image becomes cover

- GIVEN an admin is authenticated (ROLE_ADMIN)
- AND a product with id `42` exists with no images
- WHEN the admin sends `POST /api/admin/products/42/images` with a valid JPEG file
  of 2 MB as `multipart/form-data`
- THEN HTTP 201 is returned
- AND one `product_image` row exists with `product_id=42`, `is_cover=TRUE`,
  `display_order=0`
- AND `products.image_url` equals `{base-url}/api/uploads/images/{uuid}.jpg`
- AND the binary exists on the local filesystem under the configured uploads directory

#### Scenario: Successful upload — subsequent image appended

- GIVEN an admin is authenticated
- AND a product with id `42` already has one image (cover, `display_order=0`)
- WHEN the admin sends a second valid image upload for product `42`
- THEN HTTP 201 is returned
- AND the new `product_image` row has `is_cover=FALSE` and `display_order=1`
- AND the existing cover row is unchanged

#### Scenario: Upload rejected — not authenticated as admin (403)

- GIVEN the caller is not authenticated or is authenticated without ROLE_ADMIN
- WHEN `POST /api/admin/products/42/images` is called
- THEN HTTP 403 is returned
- AND no `product_image` row is created

#### Scenario: Upload rejected — product not found (404)

- GIVEN an admin is authenticated
- AND no product with id `9999` exists
- WHEN the admin sends `POST /api/admin/products/9999/images` with a valid file
- THEN HTTP 404 is returned
- AND no `product_image` row is created
- AND no file is written to disk

#### Scenario: Upload rejected — unsupported content type (400/415)

- GIVEN an admin is authenticated
- AND a product with id `42` exists
- WHEN the admin sends a GIF or PDF file
- THEN HTTP 400 (or 415) is returned with a descriptive error message
- AND no `product_image` row is created

#### Scenario: Upload rejected — file exceeds 5 MB (400/413)

- GIVEN an admin is authenticated
- AND a product with id `42` exists
- WHEN the admin uploads a valid JPEG of 6 MB
- THEN HTTP 400 (or 413) is returned
- AND no `product_image` row is created

#### Scenario: Upload rejected — image limit reached (400/409)

- GIVEN an admin is authenticated
- AND a product with id `42` already has exactly 10 images
- WHEN the admin attempts to upload an 11th image
- THEN HTTP 400 (or 409) is returned with a message stating the limit is reached
- AND no `product_image` row is created

---

### Requirement: Image Serve (CRITICAL — Path Traversal)

Images MUST be served publicly (no authentication required) via a streaming
`@RestController`. The endpoint MUST include an explicit path-traversal guard:
any filename that resolves outside the configured uploads directory MUST be
rejected before any disk I/O is performed.

**Endpoint**: `GET /api/uploads/images/{filename}` — `permitAll()`.

> **SECURITY INVARIANT**: The resolved absolute path of `{filename}` MUST start
> with the configured `uploads-dir` canonical path. If it does not — regardless
> of how the traversal was encoded — the request MUST be rejected with HTTP 400.
> The guard MUST be applied before any filesystem read. This is NON-NEGOTIABLE.

#### Scenario: Successful serve — binary returned

- GIVEN the file `abc123.jpg` exists in the uploads directory
- WHEN an unauthenticated client sends `GET /api/uploads/images/abc123.jpg`
- THEN HTTP 200 is returned
- AND the response body contains the binary content of the file
- AND the `Content-Type` header is set to `image/jpeg` (matching the stored type)

#### Scenario: File not found — 404

- GIVEN no file named `nonexistent.jpg` exists in the uploads directory
- WHEN `GET /api/uploads/images/nonexistent.jpg` is called
- THEN HTTP 404 is returned
- AND no exception is propagated to the client (no 500)

#### Scenario: Path traversal attempt — 400 (SECURITY)

- GIVEN the uploads directory is `/var/app/uploads`
- WHEN a client sends `GET /api/uploads/images/../../etc/passwd`
- THEN the resolved canonical path is computed BEFORE any I/O
- AND the path does NOT start with `/var/app/uploads`
- THEN HTTP 400 is returned
- AND no file is read from disk
- AND the application does NOT return the contents of `/etc/passwd` under any
  encoding of the traversal string

#### Scenario: Serve endpoint is public — no auth required

- GIVEN a client with no Authorization header or session
- WHEN `GET /api/uploads/images/abc123.jpg` is called and the file exists
- THEN HTTP 200 is returned (the endpoint MUST be listed under `permitAll()` in
  `SecurityConfig`)

---

### Requirement: List vs. Detail Contract

The catalog LIST endpoint MUST NOT include the `images` array in its response
(field absent or null, annotated `@JsonInclude(NON_NULL)` at field level on
`ProductResponse.images`). This preserves the existing single-query list
performance and avoids N+1 loading of lazy collections.

The product DETAIL endpoint MUST return the `images` array populated and ordered
by `display_order` ascending, plus the existing `imageUrl` (cover).

#### Scenario: LIST response — images field absent/null

- GIVEN products exist with images in the database
- WHEN `GET /api/products` is called (with or without filters)
- THEN each `ProductResponse` in the response does NOT contain an `images` property
  (field is absent or serialized as `null` and omitted by `@JsonInclude(NON_NULL)`)
- AND `imageUrl` IS present (the cover URL, or null if no images)
- AND no additional JOIN or subquery for images is executed per product

#### Scenario: DETAIL response — images populated and ordered

- GIVEN product `42` has 3 images with `display_order` 2, 0, 1 respectively
- WHEN `GET /api/products/42` is called
- THEN the response contains `images` as a non-null array of 3 `ProductImageResponse`
  objects
- AND the array order is: display_order 0, then 1, then 2 (ascending)
- AND `imageUrl` equals the URL of the image with `is_cover=TRUE`

#### Scenario: DETAIL response — product with no images

- GIVEN product `42` has no images
- WHEN `GET /api/products/42` is called
- THEN the response contains `images` as an empty array `[]`
- AND `imageUrl` is `null`

---

### Requirement: Image Delete

An admin MUST be able to delete a single image by id. The binary MUST be removed
from disk. Cover promotion rules MUST be enforced atomically.

**Endpoint**: `DELETE /api/admin/products/{id}/images/{imgId}` — ROLE_ADMIN required.

**Cover promotion rules** (all within one `@Transactional`):
- Non-cover deleted → remaining images and `products.image_url` are unchanged.
- Cover deleted and other images exist → the image with the lowest `display_order`
  among the remaining images is promoted: its `is_cover` is set to `TRUE` and
  `products.image_url` is updated to its serving URL.
- Last image deleted → `products.image_url` is set to `null`.

**Partial UNIQUE index invariant**: at most one row per product may have
`is_cover=TRUE` at any time (enforced by DB partial unique index on
`product_image(product_id) WHERE is_cover=TRUE`).

#### Scenario: Delete non-cover image

- GIVEN product `42` has images A (cover, order 0) and B (non-cover, order 1)
- WHEN admin sends `DELETE /api/admin/products/42/images/{B.id}`
- THEN HTTP 200 (or 204) is returned
- AND image B row is removed from `product_image`
- AND image A remains with `is_cover=TRUE`
- AND `products.image_url` is unchanged
- AND the binary file for B is removed from disk

#### Scenario: Delete cover image — next image promoted

- GIVEN product `42` has images A (cover, order 0) and B (non-cover, order 1)
- WHEN admin sends `DELETE /api/admin/products/42/images/{A.id}`
- THEN HTTP 200 (or 204) is returned
- AND image A row is removed from `product_image`
- AND image B now has `is_cover=TRUE`
- AND `products.image_url` equals B's serving URL
- AND no two rows have `is_cover=TRUE` for product `42`
- AND the binary for A is removed from disk

#### Scenario: Delete last image

- GIVEN product `42` has exactly one image (cover)
- WHEN admin sends `DELETE /api/admin/products/42/images/{imgId}`
- THEN HTTP 200 (or 204) is returned
- AND no `product_image` rows exist for product `42`
- AND `products.image_url` is `null`
- AND the binary is removed from disk

#### Scenario: Delete rejected — not admin (403)

- GIVEN the caller lacks ROLE_ADMIN
- WHEN `DELETE /api/admin/products/42/images/1` is called
- THEN HTTP 403 is returned
- AND no row is deleted and no file is removed

#### Scenario: Delete rejected — image not found (404)

- GIVEN admin is authenticated
- AND no image with id `9999` exists for product `42`
- WHEN `DELETE /api/admin/products/42/images/9999` is called
- THEN HTTP 404 is returned

#### Scenario: Delete rejected — product not found (404)

- GIVEN admin is authenticated
- AND no product with id `9999` exists
- WHEN `DELETE /api/admin/products/9999/images/1` is called
- THEN HTTP 404 is returned

---

### Requirement: Image Reorder

An admin MUST be able to set a new `display_order` for the product's images.
The carousel order on the frontend MUST reflect the updated order.

**Endpoint**: `PATCH /api/admin/products/{id}/images/reorder` — ROLE_ADMIN required.

**Body**: array of `{ id: Long, displayOrder: int }` covering all images of the product.

#### Scenario: Reorder updates display_order values

- GIVEN product `42` has images X (order 0), Y (order 1), Z (order 2)
- WHEN admin sends `PATCH .../42/images/reorder` with `[{id:Z, displayOrder:0}, {id:X, displayOrder:1}, {id:Y, displayOrder:2}]`
- THEN HTTP 200 is returned
- AND `product_image` rows reflect the new orders: Z=0, X=1, Y=2
- AND `is_cover` values and `products.image_url` are NOT changed by reorder

#### Scenario: Reorder rejected — not admin (403)

- GIVEN caller lacks ROLE_ADMIN
- WHEN `PATCH /api/admin/products/42/images/reorder` is called
- THEN HTTP 403 is returned

---

### Requirement: Set Cover

An admin MUST be able to promote a specific image to cover. The previous cover is
demoted. `products.image_url` is updated atomically.

**Endpoint**: `PATCH /api/admin/products/{id}/images/{imgId}/cover` — ROLE_ADMIN required.

**Invariant**: at most ONE image per product has `is_cover=TRUE` at any moment.

#### Scenario: Cover promoted — previous cover demoted

- GIVEN product `42` has image A (`is_cover=TRUE`) and image B (`is_cover=FALSE`)
- WHEN admin sends `PATCH /api/admin/products/42/images/{B.id}/cover`
- THEN HTTP 200 is returned
- AND image B has `is_cover=TRUE`
- AND image A has `is_cover=FALSE`
- AND `products.image_url` equals B's serving URL
- AND no two rows have `is_cover=TRUE` for product `42` (invariant holds)

#### Scenario: Promoting already-cover image is idempotent

- GIVEN image A is already the cover for product `42`
- WHEN admin sends `PATCH /api/admin/products/42/images/{A.id}/cover`
- THEN HTTP 200 is returned
- AND image A still has `is_cover=TRUE`
- AND `products.image_url` is unchanged

#### Scenario: Set cover rejected — not admin (403)

- GIVEN caller lacks ROLE_ADMIN
- WHEN `PATCH /api/admin/products/42/images/1/cover` is called
- THEN HTTP 403 is returned

#### Scenario: Set cover rejected — image not found (404)

- GIVEN admin is authenticated
- AND no image with id `9999` exists for product `42`
- WHEN `PATCH /api/admin/products/42/images/9999/cover` is called
- THEN HTTP 404 is returned

---

### Requirement: Frontend Carousel

The product detail view MUST render a carousel over the `images` array returned by
`GET /api/products/{id}`. The product card (list view) is unchanged — it continues
to show only the cover via `imageUrl`.

**Carousel navigation**: the user can advance to the next image or return to the
previous one. The carousel wraps (last → first, first → last) OR stops at
boundaries — the exact UX boundary behavior is a DESIGN decision.

**Fallback rule** (always applies): when `images` is empty or absent, the detail
view falls back to displaying `imageUrl ?? PLACEHOLDER_IMAGE` as a single image
(no carousel controls rendered in that state).

#### Scenario: Carousel renders N images from the images array

- GIVEN `GET /api/products/42` returns `images` with 3 items ordered by display_order
- WHEN the `ProductDetailComponent` renders
- THEN 3 image elements are accessible via carousel navigation
- AND the first displayed image corresponds to the item at index 0 (lowest display_order)

#### Scenario: Next navigation advances to the next image

- GIVEN the carousel is showing image at index 0 of 3
- WHEN the user activates the "next" control
- THEN the carousel shows the image at index 1

#### Scenario: Previous navigation goes back to the previous image

- GIVEN the carousel is showing image at index 1 of 3
- WHEN the user activates the "previous" control
- THEN the carousel shows the image at index 0

#### Scenario: Fallback — empty images array renders single imageUrl

- GIVEN `GET /api/products/42` returns `images: []` and `imageUrl: "https://…/cover.jpg"`
- WHEN `ProductDetailComponent` renders
- THEN a single image element with `src="https://…/cover.jpg"` is rendered
- AND no carousel prev/next controls are rendered

#### Scenario: Fallback — null imageUrl renders PLACEHOLDER_IMAGE

- GIVEN `GET /api/products/42` returns `images: []` and `imageUrl: null`
- WHEN `ProductDetailComponent` renders
- THEN a single image element with `src=PLACEHOLDER_IMAGE` is rendered
- AND no carousel prev/next controls are rendered

#### Scenario: Product card (list) unchanged — shows only imageUrl

- GIVEN a product in the catalog list has a non-null `imageUrl`
- WHEN the product card renders in the catalog grid
- THEN the card displays `imageUrl` (or `PLACEHOLDER_IMAGE` if null) with NO changes
  to the existing card rendering logic
- AND the `images` array (absent in list responses) is never accessed by the card

---

## Data Contract Reference

### ProductImageResponse (new DTO)

| Field | Type | Description |
|-------|------|-------------|
| `id` | `Long` | Image identifier |
| `url` | `String` | Serving URL: `{base-url}/api/uploads/images/{filename}` |
| `displayOrder` | `int` | Position in carousel (0-based) |
| `isCover` | `boolean` | Whether this is the cover image |

### ProductResponse changes (additive — no breaking change)

| Field | Change | Behaviour |
|-------|--------|-----------|
| `images` | Added | `@JsonInclude(NON_NULL)` at field level; null in LIST, populated in DETAIL |
| `imageUrl` | Unchanged | Cover URL (denormalized); still present in both LIST and DETAIL |

### DB Schema (V5 migration)

```sql
CREATE TABLE product_image (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    product_id    BIGINT      NOT NULL REFERENCES products (id),
    path          VARCHAR(500) NOT NULL,
    display_order SMALLINT    NOT NULL DEFAULT 0,
    is_cover      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_image_product ON product_image (product_id);
CREATE UNIQUE INDEX idx_product_image_cover ON product_image (product_id) WHERE is_cover = TRUE;
```

---

## Security Summary

| Endpoint pattern | Auth | Guard |
|-----------------|------|-------|
| `POST /api/admin/products/{id}/images` | ROLE_ADMIN | — |
| `DELETE /api/admin/products/{id}/images/{imgId}` | ROLE_ADMIN | — |
| `PATCH /api/admin/products/{id}/images/reorder` | ROLE_ADMIN | — |
| `PATCH /api/admin/products/{id}/images/{imgId}/cover` | ROLE_ADMIN | — |
| `GET /api/uploads/images/{filename}` | permitAll | Path traversal guard (CRITICAL) |

---

## Transactional Invariants

All state-mutating operations (upload, delete, reorder, set-cover) MUST execute
within a single `@Transactional` boundary so that the `product_image` table state
and `products.image_url` are always consistent. Partial updates (file written but
no DB row, or DB row created but `image_url` not synced) are never acceptable.
