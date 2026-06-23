# Verify Report: product-images

> SDD VERIFY phase. Change: product-images. Date: 2026-06-17.
> Artifact store: hybrid (engram `sdd/product-images/verify-report` #391 + this file).
> Spec: #383. Design: #384. Tasks: #385. Apply-progress: #386.
> Mode: Strict TDD (backend Testcontainers/MockMvc + frontend Jest).

## Verdict: PASS-WITH-WARNINGS

## Test Results

| Suite | Tests | Failures | Result |
|-------|-------|----------|--------|
| Backend `./mvnw test` | **381** | 0 | GREEN |
| Frontend `npm test -- --watchAll=false` | **107** | 0 | GREEN |

Both match apply-progress counts exactly.

## Spec Coverage — all 7 requirements

- **REQ-1 Upload** — FULLY COVERED. Type allowlist, 5MB (service 400 + real 413 via RANDOM_PORT), max 10, first-image-becomes-cover (unit ArgumentCaptor + integration), authz 401/403, product 404.
- **REQ-2 Serve (incl. path-traversal)** — FULLY COVERED. 200 + content-type via `MediaTypeFactory` + 7-day cache; 404 missing. **Path-traversal**: two independent guards — controller rejects `/`,`\`,`..` before any I/O; `resolveSafe()` canonicalize+startsWith in `LocalStorageServiceImpl`. The `..%2Fetc%2Fpasswd` integration test runs on full Spring context (`%2F` decoded before binding) → controller guard fires 400. Genuine end-to-end blocking.
- **REQ-3 List vs Detail** — FULLY COVERED. List: `toResponse()` → `images=null` → `@JsonInclude(NON_NULL)` suppresses; test asserts `$.images` absent. Detail: `toResponseWithImages()` inside `@Transactional(readOnly=true)`.
- **REQ-4 Delete** — FULLY COVERED. All 3 branches. Cover-demotion fix: demote → `entityManager.flush()` → promote; unit captures save order, integration confirms no constraint violation.
- **REQ-5 Reorder (STRICT+COMPLETE, D1)** — FULLY COVERED. Foreign ID → 400 (unit+web+integration); partial → 400; valid full set updates displayOrder; cover-neutral.
- **REQ-6 Set Cover** — COVERED (see WARNING-1). Demote+promote, idempotent, imageUrl sync unit-tested; one-cover invariant via partial unique index.
- **REQ-7 Frontend Carousel** — FULLY COVERED. next()/prev() modulo wrap (D2); empty fallback (absent → imageUrl; empty+null → PLACEHOLDER_IMAGE); nav hidden for single/no image; card uses cover only.

## CRITICAL (0)
None.

## WARNING (2)

**WARNING-1 — `setCover()` lacks `entityManager.flush()` between demote and promote.**
`ProductImageServiceImpl.java` ~lines 191–197. Same partial-unique-index race that required `flush()` in `delete()` (ADR-D5) applies identically in `setCover()`: demote current cover (is_cover=false) then promote target (is_cover=true) in the same Hibernate flush cycle. `delete()` was fixed; `setCover()` was not. If Hibernate reorders the UPDATEs, the `UNIQUE WHERE is_cover=TRUE` constraint fires as an unhandled DB violation. **Recommendation**: add `entityManager.flush()` after the demote save in `setCover()`.

**WARNING-2 — `spring.jpa.open-in-view: false` not independently confirmed.**
Required so `getById()`'s `@Transactional(readOnly=true)` boundary is MANDATORY for loading LAZY `product.images`. Apply-progress states it was set in Batch 1 and tests pass (would throw `LazyInitializationException` otherwise). Low risk; YAML not re-read this pass.

## SUGGESTION (2)

- **SUGGESTION-1** — `reorder()`/`setCover()` return 200 empty body; 204 (No Content) more accurate for void mutations. Low priority.
- **SUGGESTION-2** — No integration test for full `setCover` round-trip (upload two → PATCH cover → verify `product.imageUrl` in DB). Would also exercise WARNING-1 in real Postgres.

## Design Fidelity
All components present and wired: StorageService seam, resolveSafe guard, both controllers, ProductImageResponse, ProductResponse.images `@JsonInclude(NON_NULL)`, ProductMapper base-url ctor + toResponseWithImages(), V5 migration with partial-unique cover index (verified by SchemaIntegrationTest), SecurityConfig permitAll ordering, GlobalExceptionHandler for StorageException (500) + MaxUploadSizeExceededException (413).

## Standards
Layered architecture respected (controller never touches repository; no @Entity exposed; service = interface + impl). Flyway V5 added, applied migrations untouched. Confirmed.

## Post-Verify Resolution (2026-06-17)

- **WARNING-1 — RESOLVED.** Added `entityManager.flush()` between demote and promote in `setCover()` (mirrors `delete()` / ADR-D5). Turned out to be a **deterministic** bug, not a probabilistic race: the new integration test `setCover_promotes_target_and_demotes_previous_cover_and_syncs_image_url` FAILED in RED with `DataIntegrityViolationException` on `idx_product_image_cover` (Hibernate consistently reordered the UPDATEs), then GREEN after the flush. Suite now **382 tests, 0 failures**. See bugfix engram #392.
- **WARNING-2 — CONFIRMED OK.** `spring.jpa.open-in-view: false` is set in `application.yml` (line 9); the `getById()` `@Transactional(readOnly=true)` boundary is genuinely mandatory.
- **SUGGESTION-2 — DONE** (setCover round-trip integration test added as part of the WARNING-1 fix).
- **SUGGESTION-1** (204 vs 200) — left as optional cosmetic follow-up, not blocking.

## Next
`sdd-archive` — no open blockers. Both warnings resolved; verdict effectively PASS.
