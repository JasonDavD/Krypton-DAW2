# Proposal: Catalogo (Product Catalog + Category Management)

## Intent

Dar al e-commerce su **catalogo navegable**: que cualquier visitante (sin login) pueda
**listar, ver, buscar y filtrar** productos y categorias, y que un **ADMIN** gestione el
catalogo (alta/baja/edicion de productos y categorias). Es el cimiento de carrito y
checkout: sin catalogo no hay que comprar. Reutiliza el borde de seguridad y los patrones
ya establecidos por `auth` (JWT, split publico/ADMIN por prefijo de URL). El schema V1 ya
tiene todo lo necesario; este cambio es **puramente aditivo a nivel de codigo**.

## Scope

### In Scope
- **Category CRUD**: lectura publica (`GET /api/categories`, `/{id}`); escritura ADMIN (`POST/PUT/DELETE /api/admin/categories`).
- **Product CRUD**: lectura publica (`GET /api/products`, `/{id}`); escritura ADMIN (`POST/PUT/DELETE /api/admin/products`).
- **Search/filter** de productos: `name` (like), `category`, rango de `price`, y flag `active` — via **JPA Specifications** (`JpaSpecificationExecutor<Product>`).
- **Paginacion**: `Pageable`/`Page<T>` de Spring Data, envuelto en un DTO propio `PageResponse<T>` (nunca exponer `Page<T>`).
- **Split publico vs ADMIN**: lecturas en `/api/products` y `/api/categories`; escrituras en `/api/admin/**` (ya cubierto por la regla ADMIN existente).
- **SecurityConfig**: agregar `permitAll()` para GET en `/api/products/**` y `/api/categories/**` (hoy `anyRequest().authenticated()` bloquea las lecturas publicas).
- **Lecturas publicas solo `active = TRUE`**: el catalogo publico no filtra productos inactivos.
- **Guard de borrado de categoria**: rechazar (409/422) borrar una categoria con productos asociados, antes de que reviente la FK `category_id NOT NULL`.
- Guards de unicidad: `DuplicateSkuException`, `DuplicateCategoryNameException`.

### Out of Scope
- **Timestamps / auditoria** (`created_at`/`updated_at`) → decision explicita del usuario: NO se agregan. Por ende, "ordenar por mas reciente" NO es feature de este cambio.
- **Mutacion de stock**: `stock` es READ-ONLY en catalogo. Solo se expone; nunca se modifica via servicios de catalogo. Stock inicial admisible al crear producto como valor bootstrap (escritura directa a `products.stock`, sin `stock_movement`). El movimiento de inventario pertenece al futuro feature inventory/checkout.
- **Migracion V4**: NO se crea. El schema V1 ya tiene todas las columnas. Indices de performance (name/price) se difieren a un cambio de optimizacion.
- **Querydsl** y cualquier dependencia nueva en `pom.xml`.
- Frontend.

## Capabilities

> Mismo criterio que `auth` (split por recurso/responsabilidad: `authentication` + `user-management`).

### New Capabilities
- `product-catalog`: navegacion publica de productos (listar/ver/buscar/filtrar/paginar, solo `active=TRUE`), CRUD ADMIN de productos, unicidad de SKU y limite read-only sobre `stock`.
- `category-management`: lectura publica de categorias, CRUD ADMIN, unicidad de nombre y guard de integridad referencial al borrar (no borrar categoria con productos).

### Modified Capabilities
- `authentication`: el borde publico/protegido se amplia — GET en `/api/products/**` y `/api/categories/**` pasan a `permitAll()` en `SecurityConfig` (antes `authenticated()`).

## Approach

Reflejar exactamente los patrones de `auth`. Por recurso: **controller publico** (solo GET)
+ **AdminController** (POST/PUT/DELETE bajo `/api/admin/**`). **Service = interfaz + impl**
(`@Transactional(readOnly=true)` en lecturas, `@Transactional` en escrituras). **DTOs** como
records en `dto/request` y `dto/response`; nunca se expone el `@Entity` — `ProductMapper` /
`CategoryMapper` (`@Component`, mapeo manual). **Repos** extendidos: `ProductRepository`
suma `JpaSpecificationExecutor<Product>`; `CategoryRepository` suma chequeo de nombre
duplicado y conteo de productos por categoria. **`ProductSpecification`** con factory methods
estaticos (`nameLike`, `hasCategory`, `priceBetween`, `isActive`) compuestos con
`Specification.where(...).and(...)`. Excepciones nuevas mapeadas en `GlobalExceptionHandler`
(404 no encontrado, 409 duplicado, 422 regla de negocio). TDD RED->GREEN->REFACTOR.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `controller/{ProductController,AdminProductController,CategoryController,AdminCategoryController}.java` | New | Lecturas publicas + escrituras ADMIN |
| `service/{ProductService,CategoryService}.java` (+ `impl/`) | New | Logica de catalogo, guards de unicidad y de borrado |
| `repository/ProductRepository.java` | Mod | `extends JpaSpecificationExecutor<Product>` |
| `repository/CategoryRepository.java` | Mod | `existsByName`, conteo de productos por categoria |
| `spec/ProductSpecification.java` | New | Predicados componibles para filtros |
| `dto/request/{ProductRequest,CategoryRequest}.java` | New | Records con Bean Validation |
| `dto/response/{ProductResponse,CategoryResponse,PageResponse}.java` | New | Records de salida (stock expuesto, read-only) |
| `mapper/{ProductMapper,CategoryMapper}.java` | New | Entity<->DTO manual |
| `exception/{DuplicateSkuException,DuplicateCategoryNameException}.java` + `GlobalExceptionHandler` | New/Mod | 409/422 |
| `config/SecurityConfig.java` | Mod | `permitAll()` GET en `/api/products/**`, `/api/categories/**` |
| `model/{Product,Category}.java`, `db/migration/**` | None | Sin cambios (schema V1 suficiente) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Gap en SecurityConfig: GET publicos devuelven 401 | High | Es cambio obligatorio del scope; test integracion: GET sin token = 200 |
| Lecturas publicas filtran productos inactivos | Med | `isActive(true)` aplicado SIEMPRE en lecturas publicas; test que verifica que inactivos no aparecen |
| Borrar categoria con productos -> violacion de FK | Med | Guard en service: contar productos y rechazar (409/422) antes de tocar la DB |
| Stock mutado por error desde catalogo | Med | `stock` read-only por contrato; bootstrap solo en alta, sin `stock_movement`; comentario explicito del borde |
| Aislamiento de datos en Testcontainers compartido | Med | Setup/teardown por test; evitar dejar productos que rompan FK de tests de auth |
| Filtros opcionales mal compuestos (Specification null) | Low | Cada predicado retorna null si el filtro no viene; `where(...).and(...)` los ignora; tests por combinacion |

## Rollback Plan

Sin prod. Cambio aditivo: endpoints nuevos + **una sola** edicion en `SecurityConfig`.
`git revert` del commit revierte todo (codigo + la regla `permitAll()`). No hay migracion
nueva que deshacer; `docker compose down -v` resetea la DB local. Riesgo bajo.

## Dependencies

- `auth` completo (SecurityConfig, JWT, GlobalExceptionHandler, patrones de DTO/mapper). OK
- `backend-foundation` (entidades `Product`/`Category`, repos, schema V1). OK
- Docker Postgres corriendo para tests de integracion. **Cero dependencias nuevas** en `pom.xml`.

## Success Criteria

- [ ] `cd backend && ./mvnw test` pasa (unit + web slice + integracion).
- [ ] GET publico de productos/categorias responde 200 **sin token**; solo devuelve `active=TRUE`.
- [ ] Busqueda/filtro por name, category y rango de price funciona y pagina via `PageResponse<T>`.
- [ ] ADMIN crea/edita/borra productos y categorias; no-ADMIN recibe 403 en `/api/admin/**`.
- [ ] SKU duplicado -> 409; nombre de categoria duplicado -> 409.
- [ ] Borrar categoria con productos -> 409/422 (no rompe la FK).
- [ ] `stock` se expone en la respuesta y NUNCA es mutado por servicios de catalogo.
- [ ] La entidad nunca se expone: entran/salen solo DTOs.
