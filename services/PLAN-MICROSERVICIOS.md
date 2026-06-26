# Krypton — Migración a Microservicios (estado y plan)

> Documento de **handoff / continuidad**: para retomar exactamente donde quedamos.
> El tablero de tareas tiene el checklist de fases; este archivo guarda el CONTEXTO.

## Objetivo
Partir el monolito (`backend/`) en **9 microservicios** + infra, basándonos en
`GUIA-Microservicios-SpringBoot.md`. Demostrar y entender: Eureka, Feign, RabbitMQ, Docker, K8s.

## Estado de las fases (al cierre de esta sesión)

| Fase | Servicio | Estado |
|------|----------|--------|
| F1 | eureka-server (:8761) | ✅ hecho y verificado |
| F2 | users-service (:8081) | ✅ hecho y verificado |
| F3 | api-gateway (:8080) | ✅ hecho y verificado |
| F4 | catalog-service (:8082) | ✅ hecho y verificado |
| **F5** | **order-service (:8083) — checkout distribuido** | ⏳ **SIGUIENTE (no empezado)** |
| F6 | notification-service + RabbitMQ | pendiente |
| F7 | payment-service | pendiente |
| F8 | review-service | pendiente |
| F9 | promo-service | pendiente |
| F10 | Dockerizar todo | pendiente |
| F11 | Frontend de review/promo/payment | pendiente |
| F12 | Kubernetes (stretch) | pendiente |

**Orden de recorte si falta tiempo:** K8s (F12) → promo (F9) → review (F8). Innegociables:
users, catalog, order+checkout, notification, payment, gateway, eureka, docker.

## Arquitectura

```
Frontend → api-gateway (:8080) → [rutea por nombre vía Eureka]
  /api/auth, /api/admin/users         → users-service (:8081)   [base krypton_users]
  /api/products, /api/categories,
  /api/uploads, /api/admin/products,
  /api/admin/categories               → catalog-service (:8082) [base krypton_catalog]
  /api/cart, /api/orders, /api/admin/orders → order-service (:8083) [base krypton_orders]  (F5)

order → catalog : Feign (síncrono, descontar stock + precio en el checkout)
order → RabbitMQ → notification : evento async al pagar   (F6)
order → payment : Feign/evento    (F7)
```

## Convenciones y versiones (NO cambiar)
- Monorepo Maven en `services/`: parent pom `krypton-services` 1.0.0 (hereda spring-boot-starter-parent **3.3.5**) + BOM **spring-cloud 2023.0.3**, Java **17**. Cada servicio es un `<module>`.
- Cada servicio: clase main en **`pe.com.krypton.XxxApplication`** con `@EnableDiscoveryClient` (para que el component-scan abarque el código carvado, que conserva paquetes `pe.com.krypton.*`).
- Dependencia de cliente: `spring-cloud-starter-netflix-eureka-client` (sin version, la fija el BOM).
- `application.yml` de cada servicio: `server.port`, `spring.application.name`, datasource a su schema, `eureka.client.service-url.defaultZone: http://localhost:8761/eureka`, `app.jwt.secret` (compartido), `spring.flyway.enabled: true`, `ddl-auto: validate`.
- jjwt 0.12.6 (api compile / impl+jackson runtime).

### Patrón de seguridad STATELESS (todos los servicios MENOS users)
`users-service` emite el JWT y valida contra su DB (es dueño de users). El resto **valida sin DB**:
- `JwtService` validación-pura: `isValid`, `extractEmail`, `extractRole` (lee claim `role`). NO tiene `generateToken` ni importa la entidad `Usuario`.
- `JwtAuthenticationFilter` arma la identidad desde los claims: `ROLE_<role>` sin lookup a DB.
- `SecurityConfig` stateless, sin CustomUserDetailsService/AuthenticationManager/PasswordEncoder/CorsConfig (el CORS lo hace el gateway).
- El secreto JWT compartido (`dev-only-change-me-krypton-secret-key-1234567890`) es el "contrato" entre servicios.

### Carvado (cómo se crea cada servicio de dominio)
- Copiar del `backend/` el código del dominio conservando paquetes.
- **Trimmear** `GlobalExceptionHandler` a las excepciones presentes en ese servicio.
- **FK cruzadas → Long**: una FK a otra base (ej. `stock_movement.created_by` → users) se vuelve un `Long` sin relación JPA y sin constraint en Flyway.
- Flyway propio por servicio (esquema final, ver `docs/krypton-database.sql`).

### Gateway
`services/api-gateway/src/main/resources/application.yml`: rutas `lb://<servicio>` por path,
CORS global (origen `http://localhost:5173`) + filtro `DedupeResponseHeader` (evita el header CORS duplicado). **Al agregar rutas hay que RECOMPILAR el jar y reiniciar** (la config va dentro del jar; en Windows el jar está bloqueado mientras corre → parar, recompilar, levantar).

## Base de datos (una instancia MySQL, un schema por servicio)
Container `krypton-db` (MySQL 8), host **localhost:3307**, user `krypton`/`krypton`.
Schemas YA creados + con permisos a `krypton`@`%`: `krypton` (monolito original), `krypton_users`,
`krypton_catalog`, `krypton_orders`, `krypton_reviews`, `krypton_promos`, `krypton_payments`.

Si hay que recrearlos (DB fresca), como root:
```sql
CREATE DATABASE IF NOT EXISTS krypton_users CHARACTER SET utf8mb4;   -- (idem para los otros)
GRANT ALL PRIVILEGES ON krypton_users.* TO 'krypton'@'%';            -- (idem)
FLUSH PRIVILEGES;
```

## Cómo levantar todo localmente (los servicios corren como jars; al cerrar la sesión se caen)
```bash
docker compose up -d                       # MySQL (desde la raíz del repo)
cd services
# Orden: PRIMERO eureka, después el resto. Cada uno en su terminal o background:
java -jar eureka-server/target/eureka-server-1.0.0.jar     # :8761
java -jar users-service/target/users-service-1.0.0.jar     # :8081
java -jar catalog-service/target/catalog-service-1.0.0.jar # :8082
java -jar api-gateway/target/api-gateway-1.0.0.jar         # :8080
# (order-service :8083 cuando exista — F5)
```
Build de un servicio: `cd services && mvn -q -pl <servicio> -am package -DskipTests`.
Verificación rápida: `curl http://localhost:8761/eureka/apps` (lista los registrados);
login y catálogo por el gateway en `:8080`.

Login de prueba: `admin@krypton.pe` / `Admin123!`.

## F5 — order-service: diseño del checkout distribuido (lo SIGUIENTE)
Carvar carrito + pedidos + checkout → `order-service` (:8083, base `krypton_orders`, seguridad stateless).

**El problema:** el checkout descuenta stock, pero el stock vive en `catalog-service` (otra base).
No hay `@Transactional` que abarque dos servicios → saga + compensación.

**Plan:**
1. En **catalog-service**, exponer endpoints internos para Feign:
   - descontar stock (body: items {productId, quantity}) → ajusta `products.stock` + registra `SALIDA` en `stock_movement`, en la transacción de catalog. Falla con 409 si no hay stock.
   - **restaurar** stock (compensación).
   - (y/o) traer precio de productos para el snapshot.
2. En **order-service**: `@FeignClient(name="catalog-service")` con esos métodos. `confirmarCompra`:
   leer carrito → Feign a catalog (precio + descontar stock) → crear orden+items (snapshot de precio) localmente → vaciar carrito.
   - **Compensación**: si la creación de la orden falla DESPUÉS de descontar stock → Feign a catalog para restaurar.
3. Agregar al gateway rutas `/api/cart/**`, `/api/orders/**` → `lb://order-service`, y `/api/admin/orders/**`.
4. Verificar: checkout end-to-end por `:8080`, y el caso de compensación.

Demuestra: **Feign** (síncrono) + **saga/compensación**. Es el núcleo conceptual — hacerlo a mano y paso a paso.
