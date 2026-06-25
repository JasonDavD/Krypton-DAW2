# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es

Krypton — e-commerce B2C de artefactos tecnológicos (proyecto académico CIBERTEC, EFSRT VI).
**Monorepo**: `backend/` (Spring Boot 3.3.5 + Java 17) y `frontend/` (React 19 + Vite + TS),
lado a lado pero **se construyen y despliegan por separado**. Persistencia **MySQL 8**.

## ⚠️ Realidad vs. docs desactualizadas

`.atl/skill-registry.md` y la skill `krypton-tdd` dicen **PostgreSQL + Angular**. Eso es
historia: el plan inicial cambió. **La realidad del código** (verificá `pom.xml`,
`application.yml`, `AbstractIntegrationTest.java`, `frontend/package.json`) es:

- **MySQL 8** (no Postgres) — driver `mysql-connector-j`, `flyway-mysql`, Testcontainers `MySQLContainer<>("mysql:8")`.
- **React 19 + Vite** (no Angular).

Cuando esas skills muestren ejemplos con `PostgreSQLContainer` o `ng new`, **traducí a MySQL/React**.
El resto de cada skill (arquitectura por capas, reglas del modelo, TDD) sí aplica.

## Comandos

Hace falta DB + backend + frontend corriendo (tres terminales).

```bash
# 1. Base de datos — MySQL 8 en host:3307 (no 3306, para no chocar con un MySQL nativo)
docker compose up -d

# 2. Backend → http://localhost:8080  (Flyway corre las migraciones al arrancar)
cd backend && ./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 3. Frontend → http://localhost:5173  (apunta al back vía VITE_API_BASE_URL, default :8080)
cd frontend && npm install && npm run dev
```

**Tests (backend)** — Strict TDD activo, RED → GREEN → REFACTOR:

```bash
cd backend
./mvnw test                                                   # toda la suite (~411 tests, incluye Docker)
./mvnw test -Pfast                                            # loop de dev: SOLO unit + web slice (~313, sin Docker)
./mvnw -Dtest=ProductServiceImplTest test                     # una clase
./mvnw -Dtest=ProductServiceImplTest#should_persist_when_sku_unique test   # un solo test
```

Los ~99 tests de integración están marcados `@Tag("integration")` (heredado vía
`AbstractIntegrationTest`, salvo `ProductImageOversizeTest` que lo declara directo). El perfil
`fast` los excluye (`-DexcludedGroups=integration`) para el loop de desarrollo; `mvn test` a secas
sigue corriendo TODO para CI / pre-commit. **Regla**: en RED-GREEN-REFACTOR corré tu clase puntual
o `-Pfast`; la suite completa va antes de commitear o de la demo.

Los tests de integración levantan un **MySQL real** vía Testcontainers (no H2). La **primera
vez en cada máquina** configurá Docker para Testcontainers: `pwsh ./scripts/setup-tests.ps1`
(detalle en `docs/onboarding.md`). El entorno requiere `<testcontainers.version>1.21.4</...>`
en el pom (Docker Engine 29.x) — ya está fijado.

**Build / lint:**

```bash
cd backend && ./mvnw clean package    # JAR ejecutable en target/
cd frontend && npm run build          # tsc -b && vite build → dist/
cd frontend && npm run lint           # eslint
```

Admin sembrado por Flyway: `admin@krypton.pe` / `Admin123!`.

## Arquitectura backend — capas con interfaces

Base package `pe.com.krypton`. Las dependencias van **hacia abajo**:
`controller → service (interfaz + impl) → repository → model (@Entity)`.

Reglas de oro (no negociables — ver `docs/arquitectura-backend.md` y skill `krypton-backend`):

1. **El controller NUNCA toca el repository.** Siempre vía el service.
2. **NUNCA exponer `@Entity` en la API.** Entran/salen DTOs (`dto/request`, `dto/response`);
   la traducción Entity↔DTO vive en `mapper/`.
3. **Cada service es interfaz + impl.** El controller depende de la interfaz (mockeable en tests).
4. **Checkout = UNA transacción** (`@Transactional` en `OrderServiceImpl`): crea orden + items,
   registra `SALIDA` en `stock_movement`, descuenta `products.stock`, vacía el carrito. Todo o nada.

Paquetes más allá del CRUD básico: `security/` (JWT stateless), `policy/` (`OrderStatusPolicy`,
transiciones de estado de pedido), `spec/` (JPA Specifications para filtros de catálogo/órdenes),
`report/` (exportadores PDF con OpenPDF y Excel con Apache POI), `exception/`
(`GlobalExceptionHandler` con `@RestControllerAdvice` → respuestas `ApiError`).

### Seguridad de endpoints (`SecurityConfig`)

JWT stateless, sin sesión. `/api/auth/**` y Swagger: público. `GET /api/products/**`,
`/api/categories/**`, `/api/uploads/**`: público. **`/api/admin/**`: solo `ROLE_ADMIN`** — toda
la gestión (los `Admin*Controller`) cuelga de ahí. Todo lo demás: autenticado.

### Modelo de datos (sutil — fácil de romper)

8 tablas base (`V1`), hoy extendidas hasta `V8`. Reglas que NO son obvias:

- **Snapshot de precio**: `order_items.unit_price` se COPIA (congela) en el checkout.
  `cart_item` NO tiene columna de precio: muestra el precio VIVO del producto.
- **Stock = valor cacheado + ledger**: `products.stock` es la cantidad actual cacheada;
  `stock_movement` es el kardex (historial). Se actualizan **JUNTOS en la misma transacción**.
- **Claves**: `id` = PK surrogate (usada en TODA FK). `sku` = clave natural/de negocio (única,
  NUNCA usada como FK).
- **Enums** persistidos como `@Enumerated(EnumType.STRING)` — nunca ORDINAL.

### Flyway

Migraciones en `backend/src/main/resources/db/migration/`, `V{n}__{descripcion}.sql`. Hibernate
con `ddl-auto: validate` — **Flyway es dueño del schema, Hibernate solo valida**. **NUNCA edites
una migración ya aplicada**: agregá `V{n+1}`. El schema asume UTC (`hibernate.jdbc.time_zone: UTC`,
porque MySQL `DATETIME` no guarda zona).

## Arquitectura frontend

SPA React 19 + React Router 7. Organización **por feature** en `src/features/` (catalog, cart,
checkout, orders, auth, home, legal, admin). Dentro de cada feature se co-localizan páginas
(`*.tsx`), modales y su capa HTTP (`*.api.ts`).

- **HTTP**: `src/lib/api.ts` exporta un cliente axios con `baseURL = API_BASE_URL` (`src/config.ts`,
  desde `VITE_API_BASE_URL`). Un interceptor adjunta el `Bearer` token desde `localStorage` (`TOKEN_KEY = 'token'`).
- **Auth**: `src/auth/AuthContext.tsx` + `RequireAdmin.tsx` (guard de rutas `/admin`).
- **Estado de carrito**: `src/cart/CartContext.tsx`.
- **Modelos/tipos**: `src/models/` (auth, cart, order, product, report) — espejo de los DTOs del back.
- **Estilos**: tokens de diseño en `src/styles/design-system/` (colors, fonts, spacing, typography).
- **Rutas**: dos shells en `src/App.tsx` — `/cuenta/**` sin chrome (auth), el resto bajo `MainLayout`.

## Convenciones

- **Commits**: conventional commits ÚNICAMENTE. NUNCA agregar `Co-Authored-By` ni atribución a IA.
- **Idioma**: **comentarios en español**; identificadores (variables, métodos, campos, clases)
  **en inglés** — interoperabilidad con Spring/JPA y el contrato JSON/BD.
- **TDD estricto** está activo: el test que falla va PRIMERO. Ver skill `krypton-tdd` por capa
  (unit con Mockito mockeando la interfaz del repository, web slice con `@WebMvcTest`, integración
  con Testcontainers MySQL real). Nombres: `should_<esperado>_when_<condición>()`.

## Skills del proyecto

- `krypton-backend` — arquitectura por capas + reglas del modelo de datos (al tocar Java backend).
- `krypton-tdd` — workflow Strict TDD y patrones de test (al escribir tests / implementar features).
- `krypton-design` — guías de marca y UI kit (al generar interfaces/assets del front).

> Recordá: los ejemplos de `krypton-tdd` aún citan Postgres — la implementación real es MySQL.

## Referencias

- `docs/arquitectura-backend.md` — estructura de paquetes + reglas de oro (leer antes de codear).
- `docs/modelo-datos.md` — modelo de datos didáctico. `docs/modelo.dbml` — fuente única del ER.
- `backend/postman/*.postman_collection.json` — colecciones por dominio (Auth, Cart, Catalog, Orders, Reports).
