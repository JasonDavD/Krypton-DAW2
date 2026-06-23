# Archive Report: catalogo (krypton-ecommerce) — CICLO SDD COMPLETO

**Fecha**: 2026-06-06. **Verdict**: PASS (118/118 tests). **Rama**: `feat/catalogo`.

## Specs sincronizados a la fuente de verdad (openspec/specs/)

| Capability | Accion | Detalle |
|------------|--------|---------|
| `product-catalog` | Created (nueva) | 6 requisitos: listado publico paginado (active only), get por id, ADMIN create/update/delete (soft), stock read-only boundary |
| `category-management` | Created (nueva) | 5 requisitos: listado publico, get por id, ADMIN create/update/delete, guard referencial (count-before-delete → 409) |
| `authentication` | Modified | +1 requisito ampliado: GET `/api/products/**` y `/api/categories/**` ahora `permitAll()`, scoped a HttpMethod.GET; 3 nuevos scenarios; orden de reglas SecurityConfig documentado |

## Implementacion

- **Arquitectura**: slice aditivo que replica patrones de `auth` (controller→service(interface+impl)→repository→@Entity). Sin nuevas dependencias en pom.xml.
- **Endpoints publicos**: `GET /api/products` (filtros: name/categoryId/priceMin/priceMax, paginado via `PageResponse<ProductResponse>`); `GET /api/products/{id}`; `GET /api/categories`; `GET /api/categories/{id}`.
- **Endpoints ADMIN**: `POST/PUT/DELETE /api/admin/products`; `POST/PUT/DELETE /api/admin/categories`.
- **Filtros**: `JPA Specification` + `JpaSpecificationExecutor<Product>` + `ProductSpecification` (factories estaticos: nameLike, hasCategory, priceBetween, isActive). Cero nuevas deps.
- **Paginacion**: `PageResponse<T>` record (content, pageNumber, pageSize, totalElements, totalPages). Spring Page<T> nunca expuesto.
- **Security delta**: una sola linea en `SecurityConfig`: `.requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()` insertada ANTES de `anyRequest().authenticated()`.
- **Stock boundary**: settable solo al crear (bootstrap directo a `products.stock`, sin `stock_movement`). Update ignora el campo stock silenciosamente. Comentario de limite arquitectonico en `ProductServiceImpl`.
- **Soft delete productos**: `active=false`; las lecturas publicas siempre aplican `isActive(true)`. Producto inactivo en GET /{id} → 404.
- **Guard categorias**: `CategoryServiceImpl.delete` cuenta productos via `ProductRepository.existsByCategoryId` ANTES de tocar DB → 409 si hay productos. Nunca atrapa `DataIntegrityViolationException`.
- **Migraciones**: ninguna. Schema V1 completo; `ddl-auto: validate` sin cambios.

## Tests: 118/118 verde

Unit (JUnit5 + Mockito): `ProductServiceImplTest` (sku duplicado, stock no mutado en update, soft delete), `CategoryServiceImplTest` (nombre duplicado, guard delete).
Web slice (@WebMvcTest): `ProductControllerTest`, `AdminProductControllerTest`, `CategoryControllerTest`, `AdminCategoryControllerTest`.
Integracion (Testcontainers PostgreSQL): `CatalogIntegrationTest` — GET publico sin token 200, active=true only, filtros compuestos, paginacion, ADMIN 201/204, SKU duplicado 409, campo faltante 400, delete con productos 409, delete vacio 204, no-ADMIN 403, sin autenticar 401. Cleanup `@AfterEach` en orden FK (productos primero, luego categorias) para no romper tests de `auth` en el contenedor compartido.

## Fases completadas / tareas

| Fase | Tareas | Estado |
|------|--------|--------|
| 1 — Infrastructure | 8/8 | DONE |
| 2 — Service Layer (TDD RED→GREEN→REFACTOR) | 5/5 | DONE |
| 3 — Controller Layer (TDD RED→GREEN→REFACTOR) | 9/9 | DONE |
| 4 — Security Config Delta | 1/1 | DONE |
| 5 — Integration Tests | 6/6 | DONE |
| **Total** | **28/28** | **COMPLETE** |

## Decisiones registradas (engram)

- **#298** — Categoria delete guard cuenta productos soft-deleted (active=false). Decision aceptada: `existsByCategoryId` cuenta TODOS los productos que referencian la categoria, activos o inactivos, para no violar la FK de DB. El test `delete_category_with_soft_deleted_product_still_returns_409` aserta explicitamente el 409 para ese scenario (hardened en commit 397b877), blindando la decision contra regresiones.

## Findings del verify — RESUELTOS (commit 397b877)

- **W1 (resuelto)**: el test del guard con producto soft-deleted ahora aserta el **409 explicito** y fue renombrado a `delete_category_with_soft_deleted_product_still_returns_409`. Si alguien filtrara `active=true` en el guard (reintroduciendo el bug de FK), el test falla. La decision #298 queda blindada.
- **S1 (resuelto)**: nombres unificados a `priceMin`/`priceMax` entre `ProductController` (`@RequestParam`) y `ProductService.search()`/`ProductServiceImpl`. Sin asimetria.

## Traceabilidad (engram IDs)

| Artefacto | ID |
|-----------|-----|
| proposal | #293 |
| spec | #294 |
| design | #295 |
| tasks | #296 |
| verify-report | #299 |
| archive-report | (guardado en este ciclo) |

## Estado del ciclo

proposal → spec → design → tasks → apply (5 fases) → verify (PASS) → **archive**.
Proximo: `git mv openspec/changes/catalogo → openspec/changes/archive/2026-06-06-catalogo/` + commit + push `feat/catalogo` + PR a `main`.
