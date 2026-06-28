# Krypton E-commerce

E-commerce B2C de artefactos tecnológicos (dispositivos y componentes). Proyecto
académico **CIBERTEC — EFSRT VI**. Monorepo: **backend** Spring Boot 3 + Java 17,
**frontend** React 19 + Vite + TypeScript, persistencia en **MySQL 8**.

> El repo incluye además la **migración a microservicios** del backend (`services/`):
> el monolito repartido en **9 servicios** con Eureka, API Gateway, Feign y RabbitMQ.
> Ver la sección [Microservicios](#microservicios-services--la-migración).

## Estructura del monorepo

```
Krypton-Ecommerce
├── backend/                            # MONOLITO Spring Boot 3.3.5 + Java 17 (la app original)
│   ├── src/main/java/pe/com/krypton/   # entity, repository, service (+impl, base ICRUD),
│   │                                   #   controller/{store,admin}, dto/{request,response}, mapper, ...
│   ├── src/main/resources/             # application.yml + db/migration (Flyway)
│   └── src/test/java/pe/com/krypton/   # tests (unit + web slice + integración)
├── services/                           # MIGRACIÓN a microservicios (9 módulos Maven + gateway)
│   ├── eureka-server, api-gateway       #   infraestructura (descubrimiento + portón)
│   └── {users,catalog,order,notification,payment,review,promo}-service   # dominio (ver "Microservicios")
├── frontend/                           # React 19 + Vite + TypeScript (SPA)
│   ├── src/features/                   # catálogo, carrito, checkout, pedidos, admin, ...
│   └── src/components, models, auth/   # compartidos
├── docs/                               # documentación (guías + arquitectura + modelo de datos)
├── docker-compose.yml                  # MySQL 8 + RabbitMQ (infra para dev local)
└── docker-compose.full.yml             # stack COMPLETO containerizado (los 9 servicios + infra)
```

> **¿Por qué monorepo?** Un solo repo con `backend/` y `frontend/` lado a lado.
> Cada carpeta se construye y se despliega **por separado** (el back a Render/
> Railway, el front a Vercel/Netlify). Monorepo es dónde VIVE el código; el
> despliegue es independiente.

## Microservicios (`services/`) — la migración

Además del monolito, el repo incluye la **migración a microservicios** (arquitectura
distribuida): el código del monolito repartido en **9 servicios** que se comunican por
**Eureka** (descubrimiento por nombre), **Feign** (llamadas síncronas) y **RabbitMQ**
(eventos asíncronos). Cada servicio tiene **su propia base** (un schema por servicio).

```
services/
├── eureka-server/         :8761   registro / service discovery
├── api-gateway/           :8080   portón único (rutea por nombre, CORS)
├── users-service/         :8081   usuarios + auth (emite el JWT)       → krypton_users
├── catalog-service/       :8082   catálogo + stock                     → krypton_catalog
├── order-service/         :8083   carrito + pedidos + checkout (saga)  → krypton_orders
├── notification-service/  :8084   consume eventos de RabbitMQ          → (sin DB)
├── payment-service/       :8085   pagos                                → krypton_payments
├── review-service/        :8086   reseñas de productos                 → krypton_reviews
└── promo-service/         :8087   cupones de descuento                 → krypton_promos
```

**Patrones demostrados:** service discovery (Eureka), API gateway, JWT *stateless* (secreto
JWT compartido), **Feign + saga con compensación** (el checkout descuenta stock en otro
servicio y compensa si algo falla) y **mensajería asíncrona** (RabbitMQ). El paso a paso —
con código línea por línea — está en
[docs/guia-implementacion-microservicios.md](docs/guia-implementacion-microservicios.md).

### Levantar los microservicios

**Opción A — todo en Docker (un comando, sin instalar nada):**

```bash
docker compose -f docker-compose.full.yml up -d --build
```

Los Dockerfiles son **multi-stage**: compilan cada jar DENTRO de Docker. **No necesitás
Maven ni JDK instalados, ni construir nada antes** — clonás y levantás.

> La **primera vez** el build es lento (compila los 9 servicios en Docker y baja sus
> dependencias; quedan cacheadas para las siguientes).

Levanta MySQL + RabbitMQ + los 9 servicios + el gateway. La **única puerta pública** es el
gateway en `http://localhost:8080` (el front apunta ahí sin cambios). Consola de RabbitMQ en
`http://localhost:15672` (`guest`/`guest`).

> Es pesado (9 JVMs en contenedores). Si la máquina sufre, usá el modo jars (Opción B).

**Opción B — jars locales (más liviano):**

```bash
docker compose up -d                          # solo la infra: MySQL + RabbitMQ
cd services && mvn -q package -DskipTests      # compila los 9 jars
# luego, en orden (eureka PRIMERO), cada uno en su terminal o en background:
java -jar eureka-server/target/eureka-server-1.0.0.jar        # :8761
java -jar users-service/target/users-service-1.0.0.jar        # :8081
# ... catalog, order, notification, payment, review, promo ...
java -jar api-gateway/target/api-gateway-1.0.0.jar            # :8080  (último)
```

Login de prueba (igual que el monolito): `admin@krypton.pe` / `Admin123!`.

## Stack

| Capa | Tecnología |
| ---- | ---------- |
| Backend | Spring Boot 3.3.5, Java 17, Maven (wrapper `./mvnw`) |
| Frontend | React 19, Vite, TypeScript, React Router, Axios, Recharts |
| Persistencia | MySQL 8 + Flyway (migraciones) + Spring Data JPA |
| Seguridad | Spring Security + JWT |
| Microservicios | Spring Cloud 2023.0.3 — Eureka (discovery), API Gateway, OpenFeign |
| Mensajería | RabbitMQ (eventos asíncronos) |
| Contenedores | Docker + Docker Compose (`docker-compose.full.yml` = stack completo) |
| Tests | JUnit 5, Mockito, Testcontainers (MySQL real) |
| Arquitectura | Capas con interfaces — ver [docs/arquitectura-backend.md](docs/arquitectura-backend.md) |

## Requisitos

- **JDK 17+** — el backend usa el wrapper `./mvnw`, no hace falta instalar Maven.
- **Node 20+** (con npm) — para el frontend.
- **Docker Desktop** corriendo — para la base de datos.

## Levantar el monolito (`backend/`)

> Para levantar la **versión microservicios**, ver la sección
> [Microservicios](#microservicios-services--la-migración) más arriba.

Hacen falta **tres cosas corriendo**: la base de datos, el backend y el frontend.
Abrí una terminal para cada paso (los pasos 2 y 3 quedan en primer plano).

### 1. Base de datos (MySQL 8)

Desde la raíz del repo:

```bash
docker compose up -d
```

Levanta MySQL 8 en el puerto **3307** del host (container `krypton-db`, base
`krypton`, usuario `krypton`/`krypton`). El puerto es 3307 para no chocar con un
MySQL nativo en 3306.

### 2. Backend (http://localhost:8080)

```bash
cd backend
./mvnw spring-boot:run
```

Al arrancar, **Flyway aplica las migraciones** (crea las tablas, siembra el admin
y unos productos demo). El backend queda escuchando en `http://localhost:8080`.

> En Windows usá `mvnw.cmd spring-boot:run`.

### 3. Frontend (http://localhost:5173)

En otra terminal:

```bash
cd frontend
npm install        # sólo la primera vez
npm run dev
```

El frontend queda en `http://localhost:5173` y apunta al backend en
`http://localhost:8080` por defecto (configurable con `VITE_API_BASE_URL`).

### Listo — entrar a la tienda

Abrí **http://localhost:5173**. Podés registrar tu propia cuenta de cliente, o
entrar al **panel de administración** con el usuario sembrado:

- **Admin:** `admin@krypton.pe` / `Admin123!`

Desde `/admin` gestionás productos, categorías, pedidos, usuarios y reportes.

## Tests (backend)

```bash
cd backend
./mvnw test            # suite completa (incluye los de integración con Docker)
./mvnw test -Pfast     # loop de desarrollo: omite los de integración (sin Docker, mucho más rápido)
```

> El perfil `fast` saltea los tests marcados `@Tag("integration")` (Testcontainers MySQL).
> Usalo mientras desarrollás; corré la suite completa antes de commitear.

Los tests de integración usan **Testcontainers** (levanta un MySQL real en Docker).
La **primera vez en cada máquina** configurá Docker para Testcontainers:

```bash
pwsh ./scripts/setup-tests.ps1
```

> Sin ese paso, los tests de integración no encuentran tu Docker. El detalle está
> en [docs/onboarding.md](docs/onboarding.md).

## Build de producción

```bash
# Backend → JAR ejecutable en backend/target/
cd backend && ./mvnw clean package

# Frontend → estáticos en frontend/dist/
cd frontend && npm run build
```

## Documentación

| Doc | Contenido |
| --- | --------- |
| [docs/guia-implementacion-microservicios.md](docs/guia-implementacion-microservicios.md) | **Migración a microservicios** paso a paso (F1–F11), código línea por línea |
| [docs/rabbitmq-en-krypton.md](docs/rabbitmq-en-krypton.md) | Cómo funciona **RabbitMQ** en el proyecto (mensajería asíncrona) |
| [docs/arquitectura-backend.md](docs/arquitectura-backend.md) | Arquitectura por capas (leer antes de codear) |
| [docs/guia-backend.md](docs/guia-backend.md) · [docs/guia-frontend.md](docs/guia-frontend.md) | Guías didácticas del monolito (back y front) |
| [docs/modelo-datos.md](docs/modelo-datos.md) | Modelo de datos |
| [docs/modelo.dbml](docs/modelo.dbml) | Diagrama ER (fuente única — dbdiagram.io) |
