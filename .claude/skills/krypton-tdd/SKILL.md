---
name: krypton-tdd
description: >
  Strict TDD workflow and testing patterns for the Krypton Spring Boot backend: JUnit 5 + Mockito (unit), Spring Boot Test + Testcontainers PostgreSQL (integration), @WebMvcTest (web slice).
  Trigger: When writing tests, doing TDD (RED-GREEN-REFACTOR), or implementing any backend feature under Strict TDD.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

- ANY backend feature implementation — Strict TDD is enabled, so tests come FIRST.
- Writing unit, web-slice, or integration tests.

## Critical Patterns

- **RED → GREEN → REFACTOR.** Write the failing test FIRST, then the minimal code to pass,
  then refactor. Never write production code without a failing test driving it.
- **Test command**: `mvn test`.
- **Naming**: `should_<expected>_when_<condition>()`.

### What to test, and where

| Layer | Test type | Tool | Real DB? |
|-------|-----------|------|----------|
| service (lógica de negocio) | unit | JUnit 5 + Mockito (mock the repository **interface**) | no |
| controller (web) | slice | `@WebMvcTest` + `MockMvc` (mock the service) | no |
| repository / flujo completo | integration | `@SpringBootTest` + Testcontainers Postgres | **sí** |

- **Integration tests use REAL PostgreSQL via Testcontainers, NOT H2.** Paridad con prod:
  el kardex, las transacciones y los tipos se comportan igual que en producción.
- Mocking the repository **interface** in unit tests is exactly why services are
  interface-based (ver `krypton-backend`).

## Code Examples

```java
// UNIT — service logic, repository mocked
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {
    @Mock ProductRepository repository;
    @Mock ProductMapper mapper;
    @InjectMocks ProductServiceImpl service;

    @Test
    void should_throw_when_sku_already_exists() {
        when(repository.existsBySku("LAP-001")).thenReturn(true);
        var request = new ProductRequest("LAP-001", "Laptop", BigDecimal.TEN, 5, 1L);

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(DuplicateSkuException.class);
        verify(repository, never()).save(any());      // no se persiste si el SKU existe
    }
}

// WEB SLICE — controller, service mocked
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService service;              // Spring Boot 3.4+ (@MockBean deprecado)

    @Test
    void should_return_201_when_product_created() throws Exception {
        when(service.create(any()))
            .thenReturn(new ProductResponse(1L, "LAP-001", "Laptop", BigDecimal.TEN, 5));
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"LAP-001\",\"name\":\"Laptop\",\"price\":10,\"stock\":5}"))
           .andExpect(status().isCreated());
    }
}

// INTEGRATION — real Postgres via Testcontainers
@SpringBootTest
@Testcontainers
class ProductIntegrationTest {
    @Container @ServiceConnection                     // auto-wires el datasource (Spring Boot 3.1+)
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired ProductService service;

    @Test
    void should_persist_product_when_sku_is_unique() {
        var saved = service.create(
            new ProductRequest("LAP-001", "Laptop", new BigDecimal("999.90"), 5, null));
        assertThat(saved.id()).isNotNull();
    }
}
```

## Contenedor compartido (singleton) — OBLIGATORIO para varios IT

NO pongas un `@Container` estático en cada clase de test ni lo compartas vía una base
con `@Testcontainers`: el extension APAGA el contenedor tras la 1ª clase y la 2ª falla con
"connection has been closed". Usá el patrón **singleton** en una base sin `@Testcontainers`:

```java
@SpringBootTest
abstract class AbstractIntegrationTest {
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    static { POSTGRES.start(); }   // arranca una vez; Ryuk lo limpia al salir del JVM
}
// Los IT extienden la base: class ProductIntegrationTest extends AbstractIntegrationTest
```

> Entorno (Docker Engine 29.x): requiere `<testcontainers.version>1.21.4</testcontainers.version>`
> en el pom y `~/.testcontainers.properties` con `docker.host=npipe:////./pipe/dockerDesktopLinuxEngine`.

## Commands

```bash
mvn test                              # toda la suite
mvn -Dtest=ProductServiceImplTest test  # una sola clase
mvn -Dtest=ProductServiceImplTest#should_throw_when_sku_already_exists test  # un test
```

## Resources

- Capacidades de testing: engram `sdd/krypton-ecommerce/testing-capabilities`
- Arquitectura (por qué los services son interfaces): skill `krypton-backend`
