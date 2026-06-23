# Krypton — Arquitectura Backend (Capas con Interfaces)

**Estilo:** Arquitectura por capas con desacople vía interfaces.
**Base package:** `com.krypton` (a confirmar al generar el proyecto).
**Regla de oro de dependencias:** las dependencias van **hacia abajo**.
`controller → service → repository → base de datos`. Una capa NUNCA salta a otra
(el controller no toca el repository directo).

## Estructura de paquetes

```
com.krypton
├── KryptonApplication.java          # clase main de Spring Boot
│
├── config                           # configuración transversal
│   ├── SecurityConfig               # cadena de filtros, reglas por endpoint
│   ├── CorsConfig                   # CORS para el front React
│   └── OpenApiConfig                # Swagger / documentación de la API
│
├── security                         # autenticación/JWT
│   ├── JwtService                   # generar/validar tokens
│   ├── JwtAuthenticationFilter      # intercepta requests y valida el token
│   └── CustomUserDetailsService     # carga el usuario para Spring Security
│
├── controller                       # CAPA DE PRESENTACIÓN (REST)
│   ├── AuthController               # /api/auth  (login, register)
│   ├── ProductController            # /api/products
│   ├── CategoryController           # /api/categories
│   ├── CartController               # /api/cart
│   ├── OrderController              # /api/orders  (checkout)
│   └── ReportController             # /api/reports (PDF/Excel)
│
├── service                          # CAPA DE NEGOCIO — interfaces
│   ├── AuthService
│   ├── ProductService
│   ├── CategoryService
│   ├── CartService
│   ├── OrderService                 # orquesta el checkout transaccional
│   ├── StockService                 # registra movimientos + ajusta stock
│   ├── ReportService
│   └── impl                         # implementaciones
│       ├── AuthServiceImpl
│       ├── ProductServiceImpl
│       ├── CategoryServiceImpl
│       ├── CartServiceImpl
│       ├── OrderServiceImpl
│       ├── StockServiceImpl
│       └── ReportServiceImpl
│
├── repository                       # CAPA DE ACCESO A DATOS (Spring Data JPA)
│   ├── UserRepository
│   ├── ProductRepository
│   ├── CategoryRepository
│   ├── CartRepository
│   ├── CartItemRepository
│   ├── OrderRepository
│   ├── OrderItemRepository
│   └── StockMovementRepository
│
├── model                            # ENTIDADES JPA (@Entity)
│   ├── User
│   ├── Category
│   ├── Product
│   ├── Cart
│   ├── CartItem
│   ├── Order
│   ├── OrderItem
│   ├── StockMovement
│   └── enums
│       ├── Role                     # CLIENTE, ADMIN
│       ├── OrderStatus              # PENDIENTE, CONFIRMADA, CANCELADA
│       └── MovementType             # ENTRADA, SALIDA
│
├── dto                              # objetos de transporte (NUNCA exponer @Entity)
│   ├── request                      # lo que entra (ProductRequest, LoginRequest...)
│   └── response                     # lo que sale (ProductResponse, OrderResponse...)
│
├── mapper                           # traduce Entity ↔ DTO
│   ├── ProductMapper
│   ├── OrderMapper
│   └── ...
│
└── exception                        # manejo centralizado de errores
    ├── GlobalExceptionHandler       # @RestControllerAdvice
    ├── ResourceNotFoundException
    ├── DuplicateSkuException
    └── InsufficientStockException
```

## Responsabilidad de cada capa

| Capa | Responsabilidad | Qué NO hace |
| ---- | --------------- | ----------- |
| **controller** | Recibe HTTP, valida input, llama al service, devuelve DTO | Lógica de negocio, acceso a DB |
| **service** | Lógica de negocio, transacciones, reglas | Saber de HTTP, armar JSON |
| **repository** | Consultas a la base | Lógica de negocio |
| **model** | Mapear las tablas (entidades JPA) | Lógica de presentación |

## Reglas de oro (no negociables)

1. **El controller NUNCA toca el repository.** Siempre pasa por el service.
2. **Nunca exponer entidades `@Entity` en la API.** Entran y salen **DTOs**. Si
   exponés la entidad, acoplás tu API a tu base de datos y filtrás datos sensibles
   (ej: el `password` del User).
3. **Cada service es interfaz + impl.** El controller depende de la **interfaz**
   (`ProductService`), no de la implementación. Eso te permite mockear en los tests.
4. **El checkout va en UNA transacción** (`@Transactional` en `OrderServiceImpl`):
   crear orden + items, registrar SALIDA de stock, descontar `products.stock`,
   vaciar carrito. Todo o nada.

## Flujo de un request (ejemplo: crear producto)

```
POST /api/products
   │
   ▼
ProductController        → recibe ProductRequest (DTO), valida
   │ service.create(...)
   ▼
ProductService (impl)    → regla: SKU único; @Transactional
   │ repository.save(...)
   ▼
ProductRepository        → INSERT en la tabla products
   │
   ▼
MySQL
```
