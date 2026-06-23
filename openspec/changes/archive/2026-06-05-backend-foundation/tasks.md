# Tasks: Backend Foundation  ✅ COMPLETO (15/15) — `mvn test` 3/3 verde

> Strict TDD activo (`mvn test`). Donde aplica: RED (test que falla) → GREEN (implementar).

## Phase 1: Scaffolding (prerequisito)

- [x] 1.1 `pom.xml`: Spring Boot 3.3.5 parent, `<java.version>17</java.version>`, `<testcontainers.version>1.21.4</testcontainers.version>`, deps: web, data-jpa, security, validation, postgresql (runtime), flyway-core, flyway-database-postgresql, lombok, spring-boot-starter-test, **spring-boot-testcontainers**, testcontainers (junit-jupiter + postgresql).
- [x] 1.2 Maven wrapper (`mvnw` / `mvnw.cmd`).
- [x] 1.3 `pe/com/krypton/KryptonApplication.java` con `@SpringBootApplication`.
- [x] 1.4 `src/main/resources/application.yml`: perfiles `default`/`local`, datasource `localhost:5432/krypton`, `ddl-auto: validate`, `open-in-view: false`, flyway enabled.

## Phase 2: Schema (RED → GREEN)

- [x] 2.1 `SchemaIntegrationTest.java` (Testcontainers `postgres:16`): asserta las 8 tablas. **✅ Pasa**.
- [x] 2.2 `db/migration/V1__create_initial_schema.sql`: 8 tablas (IDENTITY, `NUMERIC(12,2)`, `TIMESTAMPTZ`, enums `VARCHAR`, UNIQUE, FKs, `cart.user_id` UNIQUE, `products.stock INT DEFAULT 0`). **✅**.

## Phase 3: Enums

- [x] 3.1 `model/enums/`: `Role`, `OrderStatus`, `MovementType`.

## Phase 4: Entidades JPA (validadas vs schema con ddl-auto validate)

- [x] 4.1 `User`, `Category`.
- [x] 4.2 `Product`: `BigDecimal` price (12,2), `int` stock, `sku` unique, `@ManyToOne(LAZY)` Category.
- [x] 4.3 `Cart` (`@OneToOne` user_id unique), `CartItem` (SIN precio).
- [x] 4.4 `Order` (`status` `@Enumerated(STRING)`), `OrderItem` (`unit_price` NOT NULL, snapshot).
- [x] 4.5 `StockMovement` (`type` STRING, `created_by` nullable), `Instant` ↔ timestamptz.

## Phase 5: Repositories

- [x] 5.1 8 interfaces `JpaRepository`: User, Category, Product, Cart, CartItem, Order, OrderItem, StockMovement.

## Phase 6: Verificación

- [x] 6.1 `KryptonApplicationTests` + `AbstractIntegrationTest` (singleton container): contexto carga → `ddl-auto: validate` OK; `all_entities_map_to_their_tables` (count por las 8 tablas).
- [x] 6.2 `mvn test` en verde: **3/3** (KryptonApplicationTests 2 + SchemaIntegrationTest 1). BUILD SUCCESS.

---

### Notas de implementación
- **Fix 1**: faltaba `spring-boot-testcontainers` (provee `@ServiceConnection`).
- **Fix 2**: `<testcontainers.version>1.21.4</testcontainers.version>` — el `docker-java` de Boot 3.3.5 no soporta Docker Engine 29.x (HTTP 400). + `~/.testcontainers.properties` con `docker.host=npipe:////./pipe/dockerDesktopLinuxEngine`.
- **Fix 3**: patrón **singleton container** en `AbstractIntegrationTest` (start manual en static block, SIN `@Testcontainers`) — compartir un `@Container` estático con `@Testcontainers` lo apaga tras la 1ª clase de test → "connection closed" en la 2ª.
