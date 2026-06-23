# Tasks: Catalogo (Product Catalog + Category Management)

> Spec refs: specs/product-catalog/spec.md · specs/category-management/spec.md · specs/authentication/spec.md
> Strict TDD: RED → GREEN → REFACTOR per task group.

---

## Phase 1: Infrastructure (foundation — no business logic)

- [x] 1.1 Extend `repository/ProductRepository`: add `extends JpaSpecificationExecutor<Product>`, `existsBySku(String)`, `existsBySkuAndIdNot(String, Long)`, `existsByCategoryId(Long)`.
- [x] 1.2 Extend `repository/CategoryRepository`: add `existsByName(String)`, `existsByNameAndIdNot(String, Long)`.
- [x] 1.3 Create `spec/ProductSpecification`: static factory methods `nameLike`, `hasCategory`, `priceBetween`, `isActive` — each returns `null` when filter absent; composed with `Specification.where().and()`.
- [x] 1.4 Create `dto/request/ProductRequest` (record, Bean Validation) and `dto/request/CategoryRequest` (record, Bean Validation) per DTO contracts in design.
- [x] 1.5 Create `dto/response/ProductResponse`, `dto/response/CategoryResponse`, `dto/response/PageResponse<T>` (record + static `of(Page<T>)` factory).
- [x] 1.6 Create `mapper/ProductMapper` and `mapper/CategoryMapper` (`@Component`, manual `toResponse(Entity)` — no MapStruct).
- [x] 1.7 Create `exception/DuplicateSkuException`, `exception/DuplicateCategoryNameException`, `exception/CategoryInUseException` (all `RuntimeException`).
- [x] 1.8 Modify `exception/GlobalExceptionHandler`: add `@ExceptionHandler` for the three new exceptions → `409 CONFLICT` with a body matching existing error-response format.

---

## Phase 2: Service Layer — RED → GREEN → REFACTOR

- [x] 2.1 **RED** — Write `ProductServiceImplTest`: failing tests for duplicate-SKU-on-create (409), update-ignores-stock, soft-delete-sets-active-false, get-by-id-inactive-throws-404.
- [x] 2.2 **GREEN** — Create `service/ProductService` (interface) + `service/impl/ProductServiceImpl`: `search(filters, Pageable)`, `getById(Long)`, `create(ProductRequest)`, `update(Long, ProductRequest)`, `delete(Long)`. Stock bootstrap on create; update mapper must NOT write stock field; delete = `active = false`.
- [x] 2.3 **RED** — Write `CategoryServiceImplTest`: failing tests for duplicate-name-on-create (409), update-own-name-excluded, delete-throws-CategoryInUseException-when-products-exist, delete-succeeds-when-no-products.
- [x] 2.4 **GREEN** — Create `service/CategoryService` (interface) + `service/impl/CategoryServiceImpl`: `list()`, `getById(Long)`, `create(CategoryRequest)`, `update(Long, CategoryRequest)`, `delete(Long)`. Delete guard: `existsByCategoryId(id)` → throw before any DB write.
- [x] 2.5 **REFACTOR** — Extract duplicated `findOrThrow` helper in both service impls; verify no business rule leaks into controllers.

---

## Phase 3: Controller Layer — RED → GREEN → REFACTOR

- [x] 3.1 **RED** — Write `@WebMvcTest` slice for `ProductController`: GET `/api/products` 200 with filters, GET `/api/products/{id}` 200/404; no token required.
- [x] 3.2 **GREEN** — Create `controller/ProductController`: `GET /api/products` (search + paginate, active-only), `GET /api/products/{id}` (404 if inactive).
- [x] 3.3 **RED** — Write `@WebMvcTest` slice for `AdminProductController`: POST 201, PUT 200, DELETE 204, validation 400, 409 mapping; 401 without token, 403 non-ADMIN.
- [x] 3.4 **GREEN** — Create `controller/AdminProductController`: `POST /api/admin/products`, `PUT /api/admin/products/{id}`, `DELETE /api/admin/products/{id}`.
- [x] 3.5 **RED** — Write `@WebMvcTest` slice for `CategoryController`: GET `/api/categories` 200, GET `/{id}` 200/404; no token required.
- [x] 3.6 **GREEN** — Create `controller/CategoryController`: `GET /api/categories`, `GET /api/categories/{id}`.
- [x] 3.7 **RED** — Write `@WebMvcTest` slice for `AdminCategoryController`: POST 201, PUT 200, DELETE 204/409; 401/403 authz.
- [x] 3.8 **GREEN** — Create `controller/AdminCategoryController`: `POST /api/admin/categories`, `PUT /api/admin/categories/{id}`, `DELETE /api/admin/categories/{id}`.
- [x] 3.9 **REFACTOR** — Align response wrapping and HTTP status codes across all four controllers; confirm no `@Entity` or `Page<T>` escapes the service boundary.

---

## Phase 4: Security Config Delta

- [x] 4.1 Modify `config/SecurityConfig`: insert `.requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()` BEFORE `anyRequest().authenticated()`. GET-only scope; non-GET remains protected.

---

## Phase 5: Integration Tests (Testcontainers PostgreSQL)

- [x] 5.1 Write integration test: `GET /api/products` returns 200 with no token + only `active=true` rows; verify `GET /api/products/{id}` on an inactive product returns 404.
- [x] 5.2 Write integration test: composed filters (name substring, categoryId, price range) return correct pages.
- [x] 5.3 Write integration test: `POST /api/admin/products` with duplicate SKU returns 409; payload with missing required field returns 400.
- [x] 5.4 Write integration test: `DELETE /api/admin/categories/{id}` returns 409 when products reference the category; returns 204 when none do.
- [x] 5.5 Write integration test: non-ADMIN user → 403 on any `/api/admin/**`; unauthenticated → 401 on admin writes.
- [x] 5.6 Add `@AfterEach` / `@Sql` cleanup in FK order (products first, then categories) to prevent catalog rows from breaking auth integration tests on the shared Testcontainers container.

---

## Parallelism Notes

| Can run in parallel | Must be sequential |
|---|---|
| 1.1 + 1.2 + 1.3 | Phase 1 → Phase 2 → Phase 3 |
| 1.4 + 1.5 + 1.6 | Phase 4 can be done alongside Phase 3 |
| 1.7 + 1.8 | Phase 5 requires Phase 3 + 4 complete |
| 2.1→2.2 (product) ∥ 2.3→2.4 (category) | |
| Controller pairs: product ∥ category | |

---

## Spec Traceability

| Task(s) | Spec requirement |
|---|---|
| 1.1–1.3 | product-catalog/spec.md — filter/pagination infra |
| 1.4–1.6 | product-catalog + category-management — DTO/mapper boundary |
| 1.7–1.8 | product-catalog (409 SKU), category-management (409 name, 409 in-use) |
| 2.1–2.2 | product-catalog — CRUD rules, stock boundary, soft delete |
| 2.3–2.4 | category-management — CRUD rules, name uniqueness, referential guard |
| 3.1–3.2 | product-catalog — public listing endpoints |
| 3.3–3.4 | product-catalog — ADMIN endpoints |
| 3.5–3.6 | category-management — public endpoints |
| 3.7–3.8 | category-management — ADMIN endpoints |
| 4.1 | authentication/spec.md — permitAll GET delta |
| 5.1–5.6 | All three specs — integration coverage incl. shared-container isolation |
