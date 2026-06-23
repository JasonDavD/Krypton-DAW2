# Tasks: Product Images — Multi-Image Carousel per Product

> SDD TASKS phase. Sources: spec #383, design #384 + `design.md` (authoritative). Artifact store: hybrid. Date: 2026-06-17.
> Execution order: P0 → P1 → P2 → P3 → P4 (backend sequential); P5 after P4; P6 PARALLEL with P5 (only needs the shared contract).
> STRICT TDD throughout: every phase opens with a RED task (write failing tests first) → GREEN task (implementation).
> Two locked decisions encoded: (D1) reorder is STRICT+COMPLETE — any foreign/unknown/partial ID set → 400; (D2) carousel WRAPS around — next on last→first, prev on first→last.

---

## Phase 0 — Data Layer: V5 Migration + Entity + Repository

> Sequential prerequisite for every backend phase. RED: extend `SchemaIntegrationTest` with table+index assertions (they fail). GREEN: write the migration.

### [x] P0.1 — RED: extend `SchemaIntegrationTest` for `product_image` table and indexes
**Spec**: REQ-1 (upload requires a `product_image` table); design §Data layer.
**Files**: `backend/src/test/java/pe/com/krypton/.../SchemaIntegrationTest.java` (MODIFY)
**Action**: Add assertions that `product_image` table exists (`information_schema.tables`), columns `id, product_id, path, display_order, is_cover, created_at` exist, and both `idx_product_image_product` and partial-unique `idx_product_image_cover` (WHERE `is_cover=TRUE`) exist in `pg_indexes`.
**Done when**: `./mvnw test -Dtest=SchemaIntegrationTest` RED — new assertions fail because V5 does not exist yet.
**Depends on**: nothing.

### [x] P0.2 — GREEN: create `V5__add_product_image.sql` migration
**Spec**: REQ-1; design §Data layer.
**Files**: `backend/src/main/resources/db/migration/V5__add_product_image.sql` (CREATE)
**Action**: Create table `product_image(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, product_id BIGINT NOT NULL REFERENCES products(id), path VARCHAR(500) NOT NULL, display_order SMALLINT NOT NULL DEFAULT 0, is_cover BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT now())`. Add `idx_product_image_product ON product_image(product_id)` and `UNIQUE idx_product_image_cover ON product_image(product_id) WHERE is_cover = TRUE`.
**Done when**: `SchemaIntegrationTest` GREEN; Flyway validates against `ddl-auto: validate`.
**Depends on**: P0.1.

### [x] P0.3 — GREEN: `ProductImage` `@Entity` + update `Product` `@OneToMany`
**Spec**: REQ-1; design §Data layer.
**Files**:
- `backend/src/main/java/pe/com/krypton/.../model/ProductImage.java` (CREATE)
- `backend/src/main/java/pe/com/krypton/.../model/Product.java` (MODIFY)
**Action**: `ProductImage` — `@Entity`, `@Table(name="product_image")`, Lombok `@Getter @Setter @NoArgsConstructor`, `@ManyToOne(fetch=LAZY) @JoinColumn(name="product_id") Product product`, `@Column(name="display_order") Short displayOrder`, `Boolean isCover`, `String path`, `@CreationTimestamp Instant createdAt` (insertable=false). `Product` — add `@OneToMany(mappedBy="product", fetch=LAZY) @OrderBy("displayOrder ASC, id ASC") List<ProductImage> images` (no cascade).
**Done when**: `./mvnw test` GREEN; Hibernate validates schema.
**Depends on**: P0.2.

### [x] P0.4 — GREEN: `ProductImageRepository`
**Spec**: REQ-4 (delete/promote), REQ-5 (reorder), REQ-6 (set-cover); design §Data layer.
**Files**: `backend/src/main/java/pe/com/krypton/.../repository/ProductImageRepository.java` (CREATE)
**Action**: `JpaRepository<ProductImage, Long>` with `long countByProductId(Long id)`, `Optional<ProductImage> findByProductIdAndIsCoverTrue(Long id)`, `Optional<ProductImage> findFirstByProductIdAndIdNotOrderByDisplayOrderAscIdAsc(Long productId, Long excludeId)`.
**Done when**: `./mvnw test` GREEN (no new tests needed beyond compile + schema validate).
**Depends on**: P0.3.

---

## Phase 1 — Storage Seam: `StorageService` Interface + `LocalStorageServiceImpl`

> Sequential after P0. RED: write path-traversal guard unit test FIRST (including `../../etc/passwd` case). GREEN: implement.

### [x] P1.1 — RED: `LocalStorageServiceImplTest` — path-traversal guard unit tests
**Spec**: REQ-2 (serve path-traversal → 400); design §Storage (resolveSafe ADR-D2).
**Files**: `backend/src/test/java/pe/com/krypton/.../storage/LocalStorageServiceImplTest.java` (CREATE)
**Action**: Write unit tests using a `@TempDir` Path as `uploadsDir`. Cover: (a) valid filename resolves normally; (b) `../../etc/passwd` → `IllegalArgumentException`; (c) `../sibling/secret.txt` → `IllegalArgumentException`; (d) `load()` on missing file → `StorageException`; (e) `delete()` on non-existent file → no exception (idempotent).
**Done when**: `./mvnw test -Dtest=LocalStorageServiceImplTest` RED — class does not exist yet.
**Depends on**: P0.4.

### [x] P1.2 — GREEN: `StorageService` interface + `LocalStorageServiceImpl`
**Spec**: REQ-2; design §Storage (ADR-D1, ADR-D2).
**Files**:
- `backend/src/main/java/pe/com/krypton/.../storage/StorageService.java` (CREATE)
- `backend/src/main/java/pe/com/krypton/.../storage/LocalStorageServiceImpl.java` (CREATE)
- `backend/src/main/java/pe/com/krypton/.../storage/StorageException.java` (CREATE)
**Action**: Interface `StorageService { String store(MultipartFile f); Resource load(String filename); void delete(String filename); }`. Impl: `@Value("${app.uploads.dir}") String rawDir` → normalize+toAbsolutePath in `@PostConstruct` + `Files.createDirectories`. `store()`: validate MIME in `ALLOWED_TYPES{image/jpeg,image/png,image/webp}`, derive extension from MIME (NOT client filename, ADR-D1), `UUID.randomUUID()+ext`, `Files.copy()`. `resolveSafe(name)`: `uploadDir.resolve(name).normalize()` → assert `startsWith(uploadDir)` else `throw new IllegalArgumentException("path traversal")`. `load()`: `resolveSafe()` → `UrlResource`; `delete()`: `resolveSafe()` → `Files.deleteIfExists()`. `StorageException` wraps `IOException` (→ 500).
**Done when**: `LocalStorageServiceImplTest` GREEN; `./mvnw test` passes.
**Depends on**: P1.1.

---

## Phase 2 — Image Service: `ProductImageService` + Impl

> Sequential after P1. RED: full unit test suite (mocked repos + StorageService, ArgumentCaptor for cover sync). GREEN: implement.

### [x] P2.1 — RED: `ProductImageServiceImplTest` unit tests (all scenarios)
**Spec**: REQ-1 (upload 7 scenarios), REQ-4 (delete 6 scenarios), REQ-5 (reorder 2 scenarios — D1 strict+complete), REQ-6 (set-cover 4 scenarios); design §Service.
**Files**: `backend/src/test/java/pe/com/krypton/.../service/ProductImageServiceImplTest.java` (CREATE)
**Action**: Mock `StorageService`, `ProductImageRepository`, `ProductRepository`. Write tests for:
- Upload: type not in allowlist → `IllegalArgumentException`; size > 5MB → `IllegalArgumentException`; product not found → `EntityNotFoundException`; count >= 10 → `IllegalArgumentException`; first image → `isCover=true` + `product.imageUrl` set (ArgumentCaptor); second image → `isCover=false`; store() only called after all validations pass.
- Delete non-cover: image deleted, no cover change; delete cover + others exist → `findFirst...IdNot` result promoted to `isCover=true` (ArgumentCaptor); delete last image → `product.imageUrl=null` + image deleted.
- Reorder STRICT (D1): body IDs don't match product's exact ID set (foreign ID) → `IllegalArgumentException`; partial set → `IllegalArgumentException`; valid complete set → `displayOrder` updated correctly.
- Set-cover: promotes new target to `isCover=true`, demotes previous cover to `isCover=false` (flush then promote in same tx); idempotent when target is already cover.
**Done when**: `./mvnw test -Dtest=ProductImageServiceImplTest` RED — service class does not exist yet.
**Depends on**: P1.2.

### [x] P2.2 — GREEN: `ProductImageService` interface + `ProductImageServiceImpl`
**Spec**: REQ-1, REQ-4, REQ-5, REQ-6; design §Service (constants, cover algorithm, ADR-D4, ADR-D5).
**Files**:
- `backend/src/main/java/pe/com/krypton/.../service/ProductImageService.java` (CREATE)
- `backend/src/main/java/pe/com/krypton/.../service/impl/ProductImageServiceImpl.java` (CREATE)
**Action**: Interface with `upload`, `delete`, `reorder`, `setCover`. Impl: `@Transactional` on all mutating methods. Constants `MAX_IMAGES_PER_PRODUCT=10`, `MAX_FILE_SIZE_BYTES=5_242_880L`, `ALLOWED_TYPES`. Validation order in upload: type → size → productExists → count → `storageService.store()`. Cover algorithm: upload sets `isCover=(count==0)`, syncs `product.imageUrl=serveUrl(filename)` on first; delete cover → `findFirstByProductIdAndIdNot...` → promote or `imageUrl=null`; setCover → `repo.findByProductIdAndIsCoverTrue` → demote → `flush()` → promote target (ADR-D5); reorder validates exact ID set match against `findAllByProductId` IDs (D1 strict) then updates `displayOrder` per-item. `serveUrl(filename)` = `baseUrl + "/api/uploads/images/" + filename`.
**Done when**: `ProductImageServiceImplTest` GREEN; `./mvnw test` passes.
**Depends on**: P2.1.

---

## Phase 3 — Web Layer: Admin Controller + Serve Controller + Security

> Sequential after P2. RED: `@WebMvcTest` tests first. GREEN: controllers + config.

### [x] P3.1 — RED: `AdminProductImageControllerTest` `@WebMvcTest` tests
**Spec**: REQ-1 (upload → 201; 400 type/size; 404 product; 409 max), REQ-4 (delete → 204; 404), REQ-5 (reorder → 200; 400 strict D1), REQ-6 (set-cover → 200); design §Web.
**Files**: `backend/src/test/java/pe/com/krypton/.../controller/AdminProductImageControllerTest.java` (CREATE)
**Action**: `@WebMvcTest(AdminProductImageController.class)` + `@MockBean ProductImageService`. Cover: multipart POST with `MockMultipartFile` (param name MUST be `"file"`) → 201; invalid type → 400; product not found → 404; DELETE → 204; PATCH reorder valid → 200; PATCH reorder foreign ID → 400; PATCH cover → 200; no auth → 401; USER role → 403.
**Done when**: `./mvnw test -Dtest=AdminProductImageControllerTest` RED — controller class missing.
**Depends on**: P2.2.

### [x] P3.2 — RED: `ImageServeControllerTest` `@WebMvcTest` tests
**Spec**: REQ-2 (serve 200 with cache-header; 404 not found; 400 path-traversal); design §Web.
**Files**: `backend/src/test/java/pe/com/krypton/.../controller/ImageServeControllerTest.java` (CREATE)
**Action**: `@WebMvcTest(ImageServeController.class)` + `@MockBean StorageService`. Cover: GET valid filename → 200 + `Cache-Control: public, max-age=604800`; GET missing file → 404; GET `../etc/passwd` (URL-encoded) → 400; permitAll (no auth header → still 200).
**Done when**: `./mvnw test -Dtest=ImageServeControllerTest` RED — controller missing.
**Depends on**: P3.1.

### [x] P3.3 — GREEN: `AdminProductImageController` + `ImageServeController` + `SecurityConfig` + `application.yml`
**Spec**: REQ-1, REQ-2, REQ-4, REQ-5, REQ-6; design §Web (ADR-D6, SecurityConfig, yml).
**Files**:
- `backend/src/main/java/pe/com/krypton/.../controller/AdminProductImageController.java` (CREATE)
- `backend/src/main/java/pe/com/krypton/.../controller/ImageServeController.java` (CREATE)
- `backend/src/main/java/.../config/SecurityConfig.java` (MODIFY — add `.requestMatchers(GET, "/api/uploads/**").permitAll()`)
- `backend/src/main/resources/application.yml` (MODIFY — add `spring.servlet.multipart.max-file-size: 5MB`, `max-request-size: 6MB`, `app.uploads.dir`, `app.uploads.base-url`)
**Action**: `AdminProductImageController` — `@RequestMapping("/api/admin/products/{productId}/images")`; `POST` (`@RequestParam MultipartFile file`) → 201; `DELETE /{imageId}` → 204; `PATCH /reorder` → 200; `PATCH /{imageId}/cover` → 200. `ImageServeController` — `GET /api/uploads/images/{filename}` → streaming `ResponseEntity<Resource>` with 7-day public cache + `MediaTypeFactory` content-type. Auth inherited from existing `/api/admin/**` rule.
**Done when**: `AdminProductImageControllerTest` + `ImageServeControllerTest` GREEN; `./mvnw test` passes.
**Depends on**: P3.2.

---

## Phase 4 — Contract/Read-Path: DTO + Mapper + ProductService Wiring

> Sequential after P3. RED: JSON/web tests for list-vs-detail contract. GREEN: DTOs, mapper, service wiring + BREAKING fix.

### [x] P4.1 — RED: `ProductControllerTest` — list excludes `images`, detail includes `images`
**Spec**: REQ-3 (list: `images` field absent; detail: `images` populated+ordered; null-in-list field absent not null).
**Files**: `backend/src/test/java/pe/com/krypton/.../controller/ProductControllerTest.java` (MODIFY)
**Action**: Add `@WebMvcTest` cases: `GET /api/products` response body — field `images` must be absent (not `null`) per `@JsonInclude(NON_NULL)`; `GET /api/products/{id}` response body — `images` array present with `id, url, displayOrder, cover` fields. Assert `url` is an absolute URL (starts with `http`).
**Done when**: New assertions RED — `ProductResponse` has no `images` field yet.
**Depends on**: P3.3.

### [x] P4.2 — GREEN: `ProductImageResponse` record + `ProductResponse.images` + `ProductMapper` + `ProductServiceImpl` wiring
**Spec**: REQ-3; design §Contract/mapping (ADR re: field-level `@JsonInclude`, `toResponseWithImages`, open-in-view:false).
**Files**:
- `backend/src/main/java/pe/com/krypton/.../dto/ProductImageResponse.java` (CREATE — record with `id, url, displayOrder, cover`)
- `backend/src/main/java/pe/com/krypton/.../dto/ProductResponse.java` (MODIFY — add `@JsonInclude(NON_NULL)` at field level on `List<ProductImageResponse> images`)
- `backend/src/main/java/pe/com/krypton/.../mapper/ProductMapper.java` (MODIFY — add `@Value("${app.uploads.base-url}") String baseUrl` ctor arg; add `toResponseWithImages()` reading `product.getImages()` + building full URL as `baseUrl + "/api/uploads/images/" + image.getPath()`)
- `backend/src/main/java/pe/com/krypton/.../service/impl/ProductServiceImpl.java` (MODIFY — `getById()` calls `toResponseWithImages()` inside existing `@Transactional(readOnly=true)`)
- `backend/src/test/java/pe/com/krypton/.../service/ProductServiceImplTest.java` (MODIFY — **fix build break**: update `new ProductMapper()` on ~line 50 to pass base-url string, e.g. `new ProductMapper("http://localhost:8080")`)
**Action**: Implement all changes atomically. `search()` stays lean (`toResponse()` — `images` remains null → serialized as absent). `getById()` uses `toResponseWithImages()` — LAZY `images` loaded inside open transaction.
**Done when**: `ProductControllerTest` new assertions GREEN; `ProductServiceImplTest` compile+pass; `./mvnw test` exits 0.
**Depends on**: P4.1.

---

## Phase 5 — Integration: `ProductImageIntegrationTest` (Testcontainers)

> Sequential after P4. Full happy-path + cover-promotion + 413 + traversal e2e + authz. Runs against singleton Postgres container.

### [x] P5.1 — RED + GREEN: `ProductImageIntegrationTest` full integration suite
**Spec**: REQ-1 through REQ-6 end-to-end; design §Testing §8.2 (`@TempDir static Path` + `@DynamicPropertySource`).
**Files**: `backend/src/test/java/pe/com/krypton/.../integration/ProductImageIntegrationTest.java` (CREATE)
**Action**: Extends `AbstractIntegrationTest` (singleton Testcontainers Postgres). `@SpringBootTest(webEnvironment=RANDOM_PORT, properties={"spring.servlet.multipart.max-file-size=5MB","spring.servlet.multipart.max-request-size=6MB"})`. `static @TempDir Path uploadsDir` + `@DynamicPropertySource` sets `app.uploads.dir` (static field → visible to static registrar). `@AfterEach` cleans `product_image` rows (FK order). Test coverage:
- Upload → DB row inserted → file on disk → `GET /api/uploads/images/{filename}` returns 200.
- First upload sets `product.image_url`; second upload does not overwrite cover.
- Delete cover + second image exists → promotion: second image becomes cover, `product.image_url` updated.
- Delete last image → `product.image_url = null`.
- Upload file > 5MB → 413 (real multipart resolver in `@SpringBootTest`).
- GET `/api/uploads/images/../etc/passwd` (URL-encoded `%2F..%2F`) → 400 path-traversal.
- POST without auth → 401; POST with USER role → 403.
- Reorder with foreign ID → 400 (D1 strict).
- Set-cover idempotent (no error when target already cover).
**Done when**: All scenarios pass; `./mvnw test -Dtest=ProductImageIntegrationTest` GREEN.
**Depends on**: P4.2.

---

## Phase 6 — Frontend Carousel (PARALLEL with P5 — depends only on shared contract)

> P6 can run concurrently with P5. Needs only the locked JSON contract (`ProductImageResponse`, `images?`) established in P4. Strict TDD (RED then GREEN).

### [x] P6.1 — RED: `product-detail.component.spec.ts` — carousel Jest tests
**Spec**: REQ-7 (carousel 6 scenarios); design §Frontend. Locked decisions: D2 wraps around.
**Files**: `frontend/src/app/features/catalog/product-detail.component.spec.ts` (MODIFY)
**Action**: Add Jest tests (before implementing carousel): (a) renders N thumbnail images when `images` has N items; (b) `next()` on last image → `currentIndex` wraps to 0 (D2); (c) `prev()` on first image → `currentIndex` wraps to `images.length - 1` (D2); (d) `hasMultiple` false when `images.length <= 1` → nav controls absent from DOM; (e) `images` empty/absent → main `<img>` src falls back to `imageUrl ?? PLACEHOLDER_IMAGE`; (f) `images` populated → main `<img>` src is `images[currentIndex].url`.
**Done when**: `cd frontend && npm test` RED — carousel logic does not exist yet; pre-existing detail tests still pass.
**Depends on**: P4.2 (`ProductImageResponse` interface established).
**Parallel with**: P5.1.

### [x] P6.2 — GREEN: update `product.model.ts` + `product-detail.component.ts` carousel impl
**Spec**: REQ-7; design §Frontend (plain-field index, modulo wrap, `hasMultiple`, fallback).
**Files**:
- `frontend/src/app/models/product.model.ts` (MODIFY — add `ProductImageResponse { id: number; url: string; displayOrder: number; cover: boolean }` interface + `images?: ProductImageResponse[]` field on `ProductResponse`)
- `frontend/src/app/features/catalog/product-detail.component.ts` (MODIFY — add `currentIndex = 0`, `gallery` getter: returns `images ?? (imageUrl ? [{url:imageUrl}] : [{url: PLACEHOLDER_IMAGE}])`, `next()`: `this.currentIndex = (this.currentIndex + 1) % this.gallery.length`, `prev()`: `this.currentIndex = (this.currentIndex - 1 + this.gallery.length) % this.gallery.length`, `hasMultiple` getter: `this.gallery.length > 1`; template: main `<img [src]="gallery[currentIndex].url">`, prev/next buttons with `*ngIf="hasMultiple"`)
**Done when**: `npm test` ALL carousel tests GREEN; `./mvnw test` still passes.
**Depends on**: P6.1.

---

## Dependency Graph

```
P0.1 → P0.2 → P0.3 → P0.4
P0.4 → P1.1 → P1.2
P1.2 → P2.1 → P2.2
P2.2 → P3.1 → P3.2 → P3.3
P3.3 → P4.1 → P4.2
P4.2 → P5.1                  (integration)
P4.2 → P6.1 → P6.2           (frontend — PARALLEL with P5)
```

## Parallelism Map

| Can run in parallel | Condition |
|---------------------|-----------|
| P5.1 and P6.1+P6.2 | After P4.2 — independent concerns |

## Locked-Decision Encoding

| Decision | Tasks |
|----------|-------|
| D1: Reorder strict+complete (foreign/partial IDs → 400) | P2.1 (RED), P2.2 (GREEN), P3.1 (web 400 test), P5.1 (integration 400) |
| D2: Carousel wraps around (next on last→first, prev on first→last) | P6.1 (RED wrap tests b+c), P6.2 (GREEN modulo impl) |

**Total tasks: 14** (P0: 4, P1: 2, P2: 2, P3: 3, P4: 2, P5: 1, P6: 2).
