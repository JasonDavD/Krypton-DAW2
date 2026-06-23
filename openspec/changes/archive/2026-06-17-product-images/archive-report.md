# Archive Report: product-images (krypton-ecommerce) — CICLO SDD COMPLETO

**Fecha**: 2026-06-17. **Verdict**: PASS (382 backend + 107 frontend = 489/489 tests). **Rama**: pendiente (uncommitted en main; branch+PR a seguir).

## Specs sincronizados a la fuente de verdad (openspec/specs/)

| Capability | Accion | Detalle |
|------------|--------|---------|
| `product-images` | Created (nueva) | 7 requisitos: upload multipart (primera = cover automático), serve con path-traversal guard, list vs detail contract (images omitido en list, poblado en detail), delete con promoción de cobertura atómica, reorder (STRICT+COMPLETE), set-cover, carousel frontend con wrap |

## Implementacion

- **Arquitectura**: slice aditivo vertical con dos controladores publicos + admin. Patrón: controller → service(interface+impl) → repository → @Entity. Seam testeable via `StorageService` interface + mock.
- **Almacenamiento**: `StorageService` abstrae todo I/O a filesystem. Impl: `LocalStorageServiceImpl` normaliza+valida rutas (path-traversal guard via `resolveSafe()`), genera UUID de filename desde MIME (no desde nombre cliente), y maneja excepciones de I/O mapeadas a 500.
- **Endpoints ADMIN**: `POST /api/admin/products/{id}/images` (multipart, 201); `DELETE /api/admin/products/{id}/images/{imgId}` (204); `PATCH .../reorder` (200); `PATCH .../{imgId}/cover` (200).
- **Endpoint publico**: `GET /api/uploads/images/{filename}` — streaming `ResponseEntity<Resource>` con cache 7-día public, `permitAll()` en `SecurityConfig`.
- **Validacion**: type allowlist (jpeg/png/webp), max 5MB (app-level 400 + real 413 en RANDOM_PORT integration), max 10 imagenes/producto, content-type SIEMPRE derivada de MIME (no de client filename — ADR-D1).
- **Cover algorithm**: (1) primer upload → `is_cover=true` + `products.image_url` sincronizado; (2) delete cover → promueve siguiente por `display_order` (lowest) o null si ultima; (3) setCover → demote actual → `entityManager.flush()` → promote destino (ADR-D5 — fix después de verify); (4) reorder → displayOrder SOLO, cover-neutral (D1).
- **DTO Contract**: `ProductImageResponse` (id/url/displayOrder/cover). `ProductResponse.images` con `@JsonInclude(NON_NULL)` field-level → omitido en LIST, poblado en DETAIL.
- **Migraciones**: V5 con tabla `product_image`, indice `idx_product_image_product`, indice UNICO PARCIAL `idx_product_image_cover (product_id) WHERE is_cover=TRUE` (DB-level one-cover invariant).
- **Seguridad**: path-traversal CRITICAL. Dos lineas de defensa: (1) controller rechaza `/`, `\`, `..` antes de I/O; (2) `resolveSafe()` canonicaliza+valida `startsWith(uploadDir)`. Integration test end-to-end: `..%2F..%2Fetc%2Fpasswd` → 400.

## Tests: 489/489 verde

Backend (Testcontainers PostgreSQL + MockMvc, Strict TDD):
- Unit (JUnit5 + Mockito): `ProductImageServiceImplTest` — validacion upload (type/size/count), upload primer imagen → cover sync (ArgumentCaptor), delete non-cover, delete cover → promotion, delete last → null, setCover demote+promote (fixed post-verify con flush), reorder STRICT (foreign IDs → 400), all 381 tests.
- Web slice (@WebMvcTest): `AdminProductImageControllerTest` (201/204/200, 400 type/size/limit, 404 product), `ImageServeControllerTest` (200 content-type+cache, 404, 400 traversal), 382 backend total (cumple apply-progress).
- Integracion (@SpringBootTest + @TempDir static): upload → DB row → file on disk → serve 200; cover sync; cover delete → promotion; last image delete → null imageUrl; 413 oversize (real multipart resolver RANDOM_PORT); path-traversal end-to-end; authz 401/403; reorder strict; setCover round-trip (ejercita flush fix).

Frontend (Jest):
- `product-detail.component.spec.ts`: carousel renders N imágenes (fixtures con images array), next/prev con modulo wrap (D2 — last→first, first→last), empty/null fallback a imageUrl / PLACEHOLDER_IMAGE, nav buttons hidden para single/no image, 107 tests.

Total backend: 382 (RED→GREEN). Total frontend: 107 (RED→GREEN). **489/489 GREEN**.

## Fases completadas / tareas

| Fase | Tareas | Estado |
|------|--------|--------|
| 0 — Data Layer (V5 + ProductImage + Product.images + ProductImageRepository) | 4/4 | DONE |
| 1 — Storage Seam (StorageService interface + LocalStorageServiceImpl, path-traversal RED first) | 2/2 | DONE |
| 2 — Image Service (ProductImageService interface + Impl, cover algorithm, RED first) | 2/2 | DONE |
| 3 — Web Layer (Admin + Serve controllers, SecurityConfig delta, RED first) | 3/3 | DONE |
| 4 — Contract/Read-Path (ProductImageResponse + ProductResponse.images + ProductMapper base-url + ProductServiceImpl wiring, RED first) | 2/2 | DONE |
| 5 — Integration Tests (ProductImageIntegrationTest, Testcontainers, all scenarios) | 1/1 | DONE |
| 6 — Frontend Carousel (product-detail.component, next/prev wrap D2, RED first) | 2/2 | DONE |
| **Total** | **14/14** | **COMPLETE** |

## Decisiones registradas (engram)

- **#382** — Proposal completo: LAZY `Product.images`, StorageService seam, denormalized `products.image_url` cover sync.
- **#383** — Spec: 7 requisitos, path-traversal CRITICAL, list vs detail contract.
- **#384** — Design: arquitectura de 5 capas, cover-promotion algorithm, ADR-D1 through ADR-D6 (MIME-ext, guard en resolveSafe, base-url ctor, reorder cover-neutral, demote-before-promote+flush, 413 integration-only).
- **#385** — Tasks: 14 items, Strict TDD (RED→GREEN para cada fase), D1 (reorder strict+complete) y D2 (carousel wrap).
- **#386** — Apply-progress: cumplimiento de tareas 100% en P0-P6, backend 382/382, frontend 107/107.
- **#391** — Verify-report (PASS-WITH-WARNINGS): 381 tests GREEN, WARNING-1 (setCover sin flush) + WARNING-2 (open-in-view no reconfirmado). Post-verify: WARNING-1 resuelto con deterministic bug fix + flush entre demote/promote (turns into 382 tests), WARNING-2 confirmado en application.yml.
- **#392** — Bugfix: setCover partial-unique race (`idx_product_image_cover`). Nueva integration test `setCover_promotes_target_and_demotes_previous_cover_and_syncs_image_url` RED con `DataIntegrityViolationException`, GREEN después de agregar `entityManager.flush()` post-demote. Deterministic, not probabilistic — Hibernate reordenaba los UPDATEs sin la linea del flush.

## Findings del verify — RESUELTOS (commit posterior a apply-batch-4)

- **WARNING-1 (RESUELTO)**: setCover faltaba `entityManager.flush()` entre demote+promote. Detectado como deterministic bug en integration test RED, solucionado al agregar flush (mirrors ADR-D5 en delete). Suite ahora **382 backend** + **107 frontend** = **489 total**.
- **WARNING-2 (CONFIRMADO)**: `spring.jpa.open-in-view: false` está en `application.yml` line 9. La boundary `@Transactional(readOnly=true)` en `getById()` es genuinamente MANDATORY (sin ella → LazyInitializationException).
- **SUGGESTION-2 (DONE)**: setCover round-trip integration test agregado en el fix de WARNING-1.
- **SUGGESTION-1 (OPCIONAL)**: 200 vs 204 para mutations — cosmetic, no blocking.

## Traceabilidad (engram IDs)

| Artefacto | ID | Notas |
|-----------|-----|-------|
| explore | #380 | 5 capas affected, trade-offs approach |
| proposal | #382 | Decisiones arquitectónicas LOCKED |
| spec | #383 | 7 requisitos, path-traversal CRITICAL |
| design | #384 | ADRs D1-D6, cover algorithm, testing strategy |
| tasks | #385 | 14 items, dependency graph, D1+D2 encoded |
| apply-progress | #386 | P0-P6 100%, backend 382, frontend 107 |
| verify-report | #391 | PASS-WITH-WARNINGS (2 resueltos post-verify) |
| bugfix | #392 | setCover flush race fix, deterministic, 382→489 |
| archive-report | (este archivo) | Guardado en este ciclo |

## Estado del ciclo

explore (#380) → proposal (#382) → spec (#383) + design (#384) → tasks (#385) → apply-progress (#386) → verify-report (#391) + bugfix (#392) → **archive**.

**Proximo paso (usuario)**: `git checkout -b feat/product-images main && git add openspec/specs/product-images/ openspec/changes/archive/2026-06-17-product-images/ && git commit -m "feat(backend+frontend): multi-image carousel per product (product-images SDD change)" && git push origin feat/product-images && gh pr create --base main`.

**Branch**: pendiente (uncommitted on main; branch+commit+PR to follow per user workflow).
