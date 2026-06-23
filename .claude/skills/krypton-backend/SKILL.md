---
name: krypton-backend
description: >
  Backend layered architecture (capas con interfaces) for Krypton e-commerce: Spring Boot + JPA + Flyway + REST/DTO conventions plus the project data-model rules (price snapshot, cached stock + kardex, surrogate vs natural key).
  Trigger: When writing or reviewing Java backend code — entities, repositories, services, controllers, DTOs, mappers, or Flyway migrations.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

- Creating or modifying ANY backend Java class (entity, repository, service, controller, DTO, mapper).
- Writing Flyway migrations.
- Reviewing backend code for architecture compliance.

## Critical Patterns (NON-NEGOTIABLE)

Base package: `pe.com.krypton`. Layered with interfaces. Dependencies flow DOWN:
`controller → service (interface + impl) → repository → model (@Entity)`.

1. **Controller NEVER touches the repository.** Always go through the service.
2. **NEVER expose `@Entity` in the API.** Requests/responses are DTOs; map Entity↔DTO in `mapper`.
3. **Every service is interface + impl.** The controller depends on the interface
   (`ProductService`), never on `ProductServiceImpl`.
4. **Checkout = ONE transaction** (`@Transactional`): create order + items, register a
   `SALIDA` in `stock_movement`, decrement `products.stock`, clear the cart. All-or-nothing.

### Data-model rules (subtle — easy to get wrong)

- **Price snapshot**: `order_item.unit_price` is COPIED (frozen) at checkout.
  `cart_item` has NO price column — it shows the LIVE product price.
- **Stock = cached value + ledger**: `products.stock` is the cached current quantity;
  `stock_movement` is the kardex (history). Update BOTH in the same transaction — never one
  without the other.
- **Keys**: `id` = surrogate PK (used in every FK). `sku` = natural/business key (unique,
  NEVER used as a FK).
- **Enums** persisted as `@Enumerated(EnumType.STRING)` — never ORDINAL.

### Flyway rules

- Migrations live in `backend/src/main/resources/db/migration/`, named `V{n}__{description}.sql`.
- `V1` creates the 8 tables. NEVER edit an already-applied migration — add a new `V{n+1}`.
- Hibernate `ddl-auto: validate` — Flyway owns the schema, Hibernate only validates.

## Package structure

```
pe.com.krypton
├── config · security · exception        (transversal)
├── controller   → REST (Auth, Product, Category, Cart, Order, Report)
├── service      → interfaces  +  service.impl (…ServiceImpl)
├── repository   → 8 interfaces Spring Data
├── model        → 8 @Entity + enums (Role, OrderStatus, MovementType)
├── dto          → request / response
└── mapper       → Entity ↔ DTO
```

## Code Examples — reference vertical slice (Product)

```java
// model/Product.java
@Entity @Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String sku;                 // natural key — never a FK
    private String name;
    private BigDecimal price;
    private int stock;                  // valor cacheado
    private boolean active;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id")
    private Category category;
    // getters/setters
}

// repository/ProductRepository.java
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySku(String sku);
}

// service/ProductService.java
public interface ProductService {
    ProductResponse create(ProductRequest request);
}

// service/impl/ProductServiceImpl.java
@Service
public class ProductServiceImpl implements ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;
    public ProductServiceImpl(ProductRepository repository, ProductMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }
    @Override @Transactional
    public ProductResponse create(ProductRequest request) {
        if (repository.existsBySku(request.sku()))
            throw new DuplicateSkuException(request.sku());
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }
}

// controller/ProductController.java
@RestController @RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;          // ← interfaz
    public ProductController(ProductService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);            // ← nunca al repository
    }
}

// dto/request/ProductRequest.java  +  dto/response/ProductResponse.java
public record ProductRequest(@NotBlank String sku, @NotBlank String name,
        @NotNull @Positive BigDecimal price, @PositiveOrZero int stock, Long categoryId) {}
public record ProductResponse(Long id, String sku, String name, BigDecimal price, int stock) {}
```

## Commands

```bash
mvn spring-boot:run          # levantar la app (con docker compose up -d antes, para la DB)
mvn compile                  # chequeo de tipos
mvn flyway:info              # estado de las migraciones
```

## Resources

- `docs/arquitectura-backend.md` — estructura completa + reglas de oro
- `docs/modelo-datos.md` — modelo de datos didáctico
- `docs/modelo.dbml` — fuente única del diagrama ER
