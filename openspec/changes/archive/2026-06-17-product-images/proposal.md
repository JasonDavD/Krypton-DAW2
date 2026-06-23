# Proposal: product-images — Multi-Image Carousel per Product

> SDD PROPOSE phase. Intent, scope, approach, and FINAL decisions on open questions.
> Architectural decisions locked in here. No code, no specs.
> Artifact store: hybrid (engram `sdd/product-images/proposal` + this file).
> Reads: `sdd/product-images/explore` (#380 / `explore.md`).
> Date: 2026-06-17.

## Intent

**Problem.** Today a `Product` carries a single `String imageUrl`. There is no way to attach
multiple pictures, no admin upload path (admin endpoints accept JSON only — no multipart), and
the product detail renders one flat `<img>`. This is below the bar of a real e-commerce, where a
product shows a gallery the customer can browse.

**Outcome.** Each product accepts **N images** uploaded by an admin. The **backend stores binaries
on the local filesystem** and the database stores **only path/reference rows** — never binaries.
The catalog **LIST** keeps using ONE **cover** per product (fast, single-table). The product
**DETAIL** shows the full **carousel** like a real store. Academic scope: the uploads directory is
ephemeral inside a container — this caveat is accepted and documented, not solved (no object storage).

**Success looks like:** an admin uploads several images to a product; the first becomes the cover;
the carousel renders all of them on detail; the card on the list shows the cover; deleting/reordering/
re-covering all behave predictably and keep `products.image_url` in sync atomically.

## Scope

### In scope
- New `product_image` table (V5 migration) storing **path references + ordering + cover flag**.
- `Product` gains a LAZY `@OneToMany` to `ProductImage`.
- **Admin upload API** (multipart): upload one image, delete, reorder, set-cover.
- **Public serving endpoint** that streams the binary from disk.
- `products.image_url` kept as a **denormalized cover** URL, synced inside `@Transactional`.
- `ProductResponse` extended with `List<ProductImageResponse> images` (null on list, populated on detail).
- **Frontend carousel** on `product-detail`; the list card is unchanged (already uses `imageUrl`).
- `StorageService` abstraction + `LocalStorageServiceImpl` for testable filesystem I/O.
- App-layer validation: content-type allowlist, max size, max images per product.

### Out of scope (explicit)
- **Product `description`** — already exists in the model and is already rendered in `product-detail`.
  NOT part of this change.
- **Object storage (S3 / MinIO)** — the production alternative to local filesystem. Not now.
- **Image resizing / thumbnails / CDN / responsive variants.** Stored and served as uploaded.
- Batch/multi-file upload in a single request (single image per request — see Approach).

## Approach (locked decisions)

These mirror the explore recommendations and are now final.

1. **Serving — streaming `@RestController`.** `GET /api/uploads/images/{filename}` returns the bytes
   with the right `Content-Type`, `permitAll`, with an **explicit path-traversal guard**. NOT a Spring
   static resource handler — the streaming controller fits the layered architecture, needs no extra MVC
   config, and is fully testable under `@WebMvcTest`.

2. **`products.image_url` — keep as denormalized cover column.** Idiomatic with the project's existing
   denormalization (cached `products.stock` + kardex, price snapshot). The LIST query stays single-table
   and the card is unchanged. The column is synced **atomically inside `@Transactional`** on cover
   change and on delete.

3. **DTO — one `ProductResponse` record + `List<ProductImageResponse> images`.** `images` is `null`
   on the list (cheap) and populated only in `getById()`. No separate `ProductDetailResponse`.

4. **Upload granularity — single image per request.** `POST /api/admin/products/{id}/images`
   (`multipart/form-data`). Simpler validation, simpler error reporting, simpler `MockMultipartFile`
   tests, per-file progress on the frontend. Plus `DELETE`, `PATCH .../reorder`, `PATCH .../{imgId}/cover`.

5. **Storage abstraction — `StorageService` interface + `LocalStorageServiceImpl`.** ALL filesystem
   I/O (store / load / delete) goes through the interface. This is the key testability lever for Strict
   TDD: unit tests mock the interface; the real impl is exercised only in integration tests.

### Data model (V5)
`product_image (id, product_id FK, path, display_order, is_cover, created_at)`, with an index on
`product_id` and a **partial unique index on `(product_id) WHERE is_cover = TRUE`** — the DB guarantees
at most one cover per product. Files land on disk under a configured uploads dir with a **UUID filename**;
the DB row stores the path, never the binary.

### Affected layers
See the **Affected Areas** table in `explore.md` (#380). Summary of the five touched layers — do not
re-derive here:
- **Migration:** new `V5__add_product_image.sql`.
- **Storage + upload:** `StorageService`/`LocalStorageServiceImpl`, `ProductImage` entity +
  repository, `ProductImageService`/impl, `AdminProductImageController`, `application.yml` multipart
  + uploads config.
- **Contract / DTO:** `ProductImageResponse` (new), `ProductResponse` (+`images`), `ProductMapper`
  (`toResponseWithImages()`), `ProductServiceImpl.getById()`.
- **Serving:** `ImageServeController`, `SecurityConfig` (`permitAll` `/api/uploads/**`).
- **Frontend:** `product.model.ts` (+`ProductImageResponse`, `images?`), `product-detail` carousel +
  spec.

## Resolved open questions

**Q1 — Serving base URL config.** DECIDED: `@Value("${app.uploads.base-url:http://localhost:8080}")`,
injected into the service that builds image URLs. Rationale: a single externalizable property keeps
local dev zero-config (default), lets the deploy override the host without code changes, and keeps the
URL-building logic in one place. The stored path stays host-agnostic; the absolute serving URL is
composed at mapping time as `{base-url}/api/uploads/images/{filename}`.

**Q2 — Cover lifecycle on delete.** DECIDED and specified:
- Deleting a **non-cover** image: just remove the row (and its file); the cover and `products.image_url`
  are untouched.
- Deleting the **cover** image: **promote the next image by `display_order`** (lowest remaining order)
  to cover, set its `is_cover = TRUE`, and update `products.image_url` to its serving URL — all in the
  same `@Transactional`.
- Deleting the **last** remaining image: no image left to promote → `products.image_url` is set to
  `null`. CONFIRMED. The product gracefully falls back to the frontend `PLACEHOLDER_IMAGE`.

**Q3 — File cleanup on product soft-delete.** DECIDED: **keep the files on disk** (academic scope).
Soft-deleting a product does NOT cascade physical file removal; orphaned files are accepted and
documented. Physical deletion of files happens only on the explicit per-image `DELETE` endpoint.
(Production would reconcile orphans with a sweep job — out of scope.)

**Q4 — `@JsonInclude(NON_NULL)` placement.** DECIDED: **field level** on the `images` property of
`ProductResponse`. Rationale: only `images` is conditionally present (null on list, populated on
detail); every other field is always serialized. Field-level placement keeps that "sometimes absent"
behavior local and explicit instead of changing the serialization contract of the whole record.

**Q5 — Max images per product.** DECIDED: **application-layer validation only**, no DB check
constraint. Rationale: a row-count limit is awkward to express portably as a DB constraint and the
business rule belongs in the service where the violation maps cleanly to a `400`. The limits are a
constant **`MAX_IMAGES_PER_PRODUCT = 10`**, a **content-type allowlist (`image/jpeg`, `image/png`,
`image/webp`)**, and a **max size of 5 MB** (also enforced via Spring multipart properties so oversized
requests are rejected before hitting the service).

## Testing note (Strict TDD)

Strict TDD is active on the backend: `cd backend && ./mvnw test`, red → green → refactor.
- **`StorageService` interface is the key testability lever** — it isolates filesystem I/O so service
  unit tests stay pure (JUnit 5 + Mockito, mock the repo + storage interfaces). Cover-sync is verified
  with an `ArgumentCaptor`.
- **Web slice** (`@WebMvcTest`): upload via **`MockMultipartFile`**; assert `400` on wrong content-type
  and oversized file, `404` on missing product. Serve endpoint: `200` + body, `404` on missing file.
- **Integration** (`@SpringBootTest` + **Testcontainers Postgres**, NO H2): upload → DB row → file on
  disk (uploads dir pointed at a `@TempDir`) → serve `200`. Extend `SchemaIntegrationTest` to assert the
  new table + indexes.
- **Security:** explicit path-traversal test (`../../etc/passwd` → `400`).
- **Frontend (Jest):** `product-detail.component.spec.ts` renders N carousel slides and exercises
  prev/next; mock fixture gains an `images` array.

## Risks

1. **Path traversal on the serving endpoint (CRITICAL).** Sanitize the filename, resolve against the
   uploads dir, and assert the canonical resolved path `startsWith(uploadDir)` — reject otherwise. Covered
   by an explicit security test. This is risk #1 and must not be skipped.
2. **Ephemeral uploads dir in containers.** Files vanish on container restart (academic scope, accepted).
   Integration tests use `@TempDir`; the caveat is documented.
3. **`products.image_url` sync atomicity** on cover change/delete — all mutations happen inside one
   `@Transactional`; partial updates must not leak.
4. **Lazy `Product.images` access** — must be read inside an open `@Transactional(readOnly = true)` in
   `getById()` to avoid `LazyInitializationException`.
5. **`@WebMvcTest` multipart setup is non-trivial** — the `MockMultipartFile` + max-size-override pattern
   must be spelled out in the tasks so apply doesn't stall.

## Verdict

Ready for `sdd-spec` and `sdd-design` (can run in parallel). All five open questions resolved; all
architectural decisions locked.
