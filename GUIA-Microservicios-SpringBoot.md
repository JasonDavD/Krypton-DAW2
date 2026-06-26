# Guía de Conocimiento — Microservicios con Spring Boot (DAW II)

> Documento de referencia consolidado a partir de los laboratorios del curso.
> Pensado para usarse como **contexto al refactorizar otro proyecto**: cada sección
> tiene **el concepto (el porqué)**, **el patrón canónico (template copy-ready)** y
> **gotchas** que evitan que pierdas horas.

---

## 0. Mapa mental: ¿hacia qué arquitectura apuntan estos labs?

Todos los labs construyen, por partes, una **arquitectura de microservicios**:

```
                 ┌──────────────┐
                 │   Eureka     │  ← Service Discovery (registro de servicios)
                 │  (8761)      │
                 └──────┬───────┘
        registro/lookup │
        ┌───────────────┼────────────────┐
        ▼               ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ users-service│  │products-svc  │  │  otros...    │
│  (8081)      │◄─┤  (8082)      │  │              │
└──────────────┘  └──────┬───────┘  └──────────────┘
   Feign (síncrono)      │
                         │ eventos (asíncrono)
                         ▼
                  ┌──────────────┐
                  │   RabbitMQ   │  ← Mensajería / desacople
                  │ (5672/15672) │
                  └──────────────┘

   Todo corre dentro de CONTENEDORES Docker (misma red)
   y en producción se orquesta con KUBERNETES (réplicas, escalado, balanceo).
```

**Las 3 decisiones de comunicación que tenés que saber elegir:**

| Necesidad | Herramienta | Cuándo |
|-----------|-------------|--------|
| Llamar a otro servicio y **esperar la respuesta ahora** | **Feign** (síncrono) | Necesitás el dato para responder en el mismo request |
| Resolver **dónde está** ese servicio sin URLs fijas | **Eureka** | Hay más de una instancia, escalado, o no querés hardcodear hosts |
| **Disparar un evento y seguir** sin esperar | **RabbitMQ** (asíncrono) | Notificaciones, tareas pesadas, desacoplar servicios |

Regla mental: **si necesitás la respuesta para continuar → Feign. Si podés "disparar y olvidar" → RabbitMQ.**

---

## 1. Convenciones transversales (aplican a TODOS los servicios)

Estas son las reglas que se repiten en todos los labs. Si refactorizás, alineá el proyecto a esto primero.

### 1.1 Estructura de paquetes
```
src/main/java/com/empresa/servicio/
 ├── config/        → @Configuration (RabbitMQ, beans, converters)
 ├── controller/    → @RestController (capa HTTP)
 ├── service/       → @Service (lógica de negocio)
 ├── repository/    → interfaces JpaRepository (acceso a datos)
 ├── model/         → @Entity (entidades JPA)
 ├── dto/           → objetos de transferencia (mensajes, payloads)
 ├── client/        → interfaces @FeignClient
 ├── producer/      → publicadores RabbitMQ
 └── consumer/      → listeners RabbitMQ
```

### 1.2 Inyección por constructor (NO `@Autowired` en campos)
Todos los labs inyectan por constructor. Es la práctica correcta: dependencias inmutables (`final`), testeable, sin sorpresas de nulos.

```java
@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService productoService;          // final

    public ProductoController(ProductoService productoService) {  // por constructor
        this.productoService = productoService;
    }
}
```

Con Lombok podés ahorrarte el boilerplate:
```java
@Service
@RequiredArgsConstructor   // genera el constructor con los campos final
public class ProducerService {
    private final RabbitTemplate rabbitTemplate;
}
```

### 1.3 Versiones que se usaron (compatibilidad Spring Boot ↔ Spring Cloud)
La compatibilidad acá es CRÍTICA. Spring Cloud va atada a una versión de Spring Boot. Mezclar mal = errores de arranque.

| Spring Boot | Spring Cloud (`spring-cloud.version`) | Java |
|-------------|---------------------------------------|------|
| 3.2.0       | 2023.0.1                              | 17   |
| 3.3.5       | 2023.0.3                              | 21   |

> **Gotcha:** Spring Cloud NO se versiona por dependencia, sino con un BOM en
> `<dependencyManagement>` (ver template abajo). Nunca le pongas versión a cada
> starter de cloud a mano.

---

## 2. Comunicación SÍNCRONA — Feign Client

### Concepto
Feign te deja consumir una API REST (externa o de otro microservicio) **declarando una interfaz**, sin escribir `RestTemplate`/`WebClient` a mano. Vos describís el endpoint; Spring genera la implementación. Es **síncrono**: la llamada bloquea hasta tener respuesta.

### Patrón

**1) Activar Feign en la app principal:**
```java
@SpringBootApplication
@EnableFeignClients          // ← habilita el escaneo de @FeignClient
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**2) Declarar el cliente como interfaz:**
```java
// Contra una API EXTERNA → se fija la url
@FeignClient(name = "jsonplaceholder", url = "https://jsonplaceholder.typicode.com")
public interface JsonPlaceholderClient {
    @GetMapping("/posts/{id}")
    Post getPostById(@PathVariable("id") Long id);
}

// Contra OTRO microservicio vía Eureka → SIN url, solo el name registrado
@FeignClient(name = "users-service")   // = spring.application.name del otro servicio
public interface UserClient {
    @GetMapping("/users")
    List<String> getUsers();
}
```

**3) Inyectar y usar:**
```java
@RestController
@RequestMapping("/products")
public class ProductController {
    private final UserClient userClient;

    public ProductController(UserClient userClient) {
        this.userClient = userClient;
    }

    @GetMapping
    public Map<String, Object> getProducts() {
        List<String> users = userClient.getUsers();   // llamada remota como si fuera local
        return Map.of("products", List.of("Laptop", "Mouse"), "users", users);
    }
}
```

### `pom.xml` — dependencia + BOM
```xml
<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Gotchas
- **`url` fija solo para APIs externas.** Para servicios internos, omití `url` y dejá que Eureka resuelva por `name`. Si hardcodeás `localhost:8081`, perdés el balanceo y rompés en producción.
- El `name` del `@FeignClient` debe coincidir EXACTO con el `spring.application.name` del servicio destino.
- `@EnableFeignClients` va una sola vez, en la clase `@SpringBootApplication`.

---

## 3. Service Discovery — Eureka (+ Feign + LoadBalancer)

### Concepto
En microservicios las instancias **cambian de IP/puerto** y se escalan. Hardcodear URLs no escala. **Eureka** es un registro: cada servicio se registra al arrancar, y los demás lo buscan por nombre. Sumado a Spring Cloud LoadBalancer, las peticiones se **reparten entre instancias** sin tocar el código del cliente Feign.

### Patrón — 1) Eureka Server (puerto 8761)
```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```
```yaml
# application.yml del SERVER
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false   # el server no se registra a sí mismo
    fetch-registry: false
```
Dependencia: `spring-cloud-starter-netflix-eureka-server`. Panel: `http://localhost:8761`.

### Patrón — 2) Cada servicio cliente se registra
```yaml
# application.yml de CADA microservicio
spring:
  application:
    name: users-service      # ← nombre con el que lo encontrarán los demás
server:
  port: 8081
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
```
Dependencia: `spring-cloud-starter-netflix-eureka-client`.

### Cómo se conecta con Feign
El `@FeignClient(name = "users-service")` resuelve la dirección **a través de Eureka**, no con URL fija. Orden de arranque:
```
1º  eureka-server
2º  users-service   (se registra)
3º  products-service (descubre a users-service por nombre)
```

### Balanceo automático (bonus del lab)
Levantás otra instancia:
```bash
java -jar users-service.jar --server.port=8083
```
Eureka muestra ambas (`8081` y `8083`) y LoadBalancer reparte las peticiones **sin cambiar una línea** del cliente Feign. Esa es la ventaja central de Eureka.

### Gotchas
- Arrancá **primero el server**; si no, los clientes loguean error de registro (reintentan, pero ensucia logs).
- `spring.application.name` ES el contrato. Cambiarlo rompe a todos los que llaman por ese nombre.

---

## 4. Comunicación ASÍNCRONA — RabbitMQ

### Concepto
RabbitMQ es un **broker de mensajes**: comunica apps de forma **asíncrona**. El productor publica y sigue; el consumidor procesa cuando puede. Sirve para desacoplar microservicios, tareas en segundo plano, notificaciones, emails.

**Vocabulario que NO podés confundir:**
| Término | Qué es |
|---------|--------|
| **Producer** | Publica mensajes |
| **Consumer** | Recibe/procesa mensajes |
| **Queue** | Cola donde se guardan los mensajes |
| **Exchange** | Recibe del producer y decide a qué cola(s) enrutar |
| **Routing Key** | Clave que usa el exchange para decidir el destino |

Flujo: `Producer → Exchange → (routing key) → Queue → Consumer`

### Levantar RabbitMQ (Docker)
```bash
docker run -d --hostname rabbit-host --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```
- `5672` → protocolo AMQP (lo usa la app). `15672` → panel web.
- Panel: `http://localhost:15672` — user/pass: `guest` / `guest`.

### Dependencia + config
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### Patrón completo (con Exchange + Routing Key — la forma "real")
```java
@Configuration
public class RabbitMQConfig {
    public static final String QUEUE       = "cola.demo";
    public static final String EXCHANGE    = "exchange.demo";
    public static final String ROUTING_KEY = "demo.key";

    @Bean Queue queue() { return new Queue(QUEUE); }

    @Bean DirectExchange exchange() { return new DirectExchange(EXCHANGE); }

    @Bean Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    // Para enviar/recibir objetos como JSON (no solo String):
    @Bean MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

**Producer:**
```java
@Service
@RequiredArgsConstructor
public class ProducerService {
    private final RabbitTemplate rabbitTemplate;

    public void send(Order order) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_KEY,
            order);                 // se serializa a JSON gracias al converter
    }
}
```

**Consumer:**
```java
@Service
public class ConsumerService {
    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void receive(Order order) {
        System.out.println("Recibido: " + order);
    }
}
```

### Tipos de Exchange (elegí según el patrón de enrutamiento)
| Tipo | Comportamiento | Caso de uso |
|------|----------------|-------------|
| **direct** | Routing key EXACTA | `order.created` → una cola específica |
| **fanout** | A TODAS las colas (ignora routing key) | Logs, eventos globales (broadcast) |
| **topic** | Patrones con comodines | `order.*`, `user.#` — muy usado en microservicios |
| **headers** | Por headers del mensaje | Menos común |

### Buenas prácticas (del lab) + producción
- Mensajes **pequeños** y en **JSON** (interoperable). No mandes archivos.
- **Consumers idempotentes**: procesar dos veces el mismo mensaje no debe romper nada.
- **Dead Letter Queue (DLQ)**: a dónde van los mensajes que fallan, para auditoría/reproceso/debug. Usala siempre en prod.
- Soporta **ACK manual** y **reintentos**.
- Monitoreo: RabbitMQ Management + Prometheus/Grafana.

### Arquitectura típica (ej. e-commerce)
```
Order Service → RabbitMQ → Payment Service
                        → Shipping Service
                        → Notification Service
```
Cada microservicio procesa el evento **independientemente**. Si uno cae, los demás siguen.

### RabbitMQ vs Kafka (para saber elegir)
| RabbitMQ | Kafka |
|----------|-------|
| Broker de colas | Event streaming |
| Fácil de usar | Más complejo |
| Ideal tareas/colas | Ideal big data |
| Baja latencia | Alto throughput |

---

## 5. Contenerización — Docker

### Concepto
Docker empaqueta tu app **con todo lo que necesita** (runtime, dependencias, config) en un **contenedor** portable y aislado. "Funciona en mi máquina" deja de ser excusa.

### Comandos base (CORREGIDOS — los labs tienen typos)
```bash
docker --version                      # verificar instalación
docker info                           # ver si el daemon corre
docker pull nginx                     # descargar imagen
docker images                         # listar imágenes locales
docker run hello-world                # crear y correr un contenedor
docker ps                             # contenedores EN EJECUCIÓN
docker ps -a                          # TODOS (incluye detenidos)
docker run -d nginx                   # -d = detached (segundo plano)
docker run --name web nginx           # asignar nombre
docker run -d -p 8080:80 nginx        # mapear puerto host:contenedor
docker run -it --name u1 ubuntu:20.04 # interactivo (-it), imagen en MINÚSCULA
docker start u1 / docker stop u1      # iniciar / detener
docker exec -it u1 bash               # ejecutar comando DENTRO del contenedor
docker logs <contenedor>              # ver logs
docker rm -f <contenedor>             # eliminar (forzado)
```

> **Correcciones respecto a los labs** (si copiabas tal cual, no andaba):
> - `docker run Ubuntu` → **`docker run ubuntu`** (los nombres de imagen son
>   case-sensitive y van en minúscula).
> - `docker excec` → **`docker exec`**.
> - `doceker start` → **`docker start`**.

### Imágenes oficiales más usadas
`ubuntu`, `alpine` (mínima/liviana), `nginx`, `mysql`, `postgres`, `redis`, `node`, `python`, `mongo`, `httpd`, `memcached`, `rabbitmq`, `busybox`, `hello-world`.

### Redes (clave para que los contenedores se hablen)
```bash
docker network create my-network
```
Dentro de una red, **el nombre del contenedor funciona como hostname**. Por eso la app se conecta a `my-mysql:3306`, no a `localhost`.

### MySQL en contenedor (con persistencia)
```bash
docker run -d \
  --name my-mysql \
  --network my-network \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=mydb \
  -e MYSQL_USER=user \
  -e MYSQL_PASSWORD=pass \
  -v mysql_data:/var/lib/mysql \   # ← volumen = los datos sobreviven al reinicio
  -p 3306:3306 \
  mysql:8
```
| Flag | Para qué |
|------|----------|
| `-e MYSQL_*` | Variables de entorno: root pass, DB inicial, usuario |
| `-v mysql_data:/var/lib/mysql` | **Volumen**: persiste datos fuera del contenedor |
| `-p 3306:3306` | Expone el puerto al host |

> **Gotcha de volúmenes:** sin `-v`, al borrar el contenedor **perdés los datos**.
> `docker volume rm mysql_data` BORRA los datos: cuidado en prod.

---

## 6. Dockerizar el backend Spring Boot + MySQL (el lab integrador)

Este es el patrón que más vas a reusar al refactorizar: **API + base de datos, ambas en contenedores en la misma red.**

### 6.1 `application.properties` apuntando al contenedor de MySQL
```properties
# OJO: el host es el NOMBRE DEL CONTENEDOR (my-mysql), no localhost
spring.datasource.url=jdbc:mysql://my-mysql:3306/mydb
spring.datasource.username=user
spring.datasource.password=pass
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

### 6.2 Stack JPA estándar (Entity → Repository → Service → Controller)
```java
// MODEL
@Entity
public class Producto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private Double precio;
    // constructores + getters/setters
}

// REPOSITORY — Spring Data te da el CRUD gratis
public interface ProductoRepository extends JpaRepository<Producto, Long> {}

// SERVICE — lógica de negocio, inyección por constructor
@Service
public class ProductoService {
    private final ProductoRepository repo;
    public ProductoService(ProductoRepository repo) { this.repo = repo; }

    public List<Producto> getAll()            { return repo.findAll(); }
    public Optional<Producto> getById(Long id){ return repo.findById(id); }
    public Producto save(Producto p)          { return repo.save(p); }
    public void delete(Long id)               { repo.deleteById(id); }
}

// CONTROLLER — usa ResponseEntity para manejar 200 / 404 / 204
@RestController
@RequestMapping("/productos")
public class ProductoController {
    private final ProductoService service;
    public ProductoController(ProductoService service) { this.service = service; }

    @GetMapping
    public List<Producto> getAll() { return service.getAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> getById(@PathVariable Long id) {
        return service.getById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());     // 404 limpio
    }

    @PostMapping
    public Producto create(@RequestBody Producto p) { return service.save(p); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (service.getById(id).isPresent()) {
            service.delete(id);
            return ResponseEntity.noContent().build();       // 204
        }
        return ResponseEntity.notFound().build();
    }
}
```

> **Mejor práctica (no estaba en el lab, pero importa al refactorizar):** no
> expongas la `@Entity` directo en el controller. Para algo más serio, meté un
> **DTO** entre la entidad y el JSON. Te desacopla el modelo de persistencia del
> contrato de la API. En estos labs se usa la entidad directa por simplicidad
> didáctica — está bien para aprender, no para un sistema que va a crecer.

### 6.3 Dockerfile del backend
```dockerfile
FROM amazoncorretto:17-al2-jdk      # (el lab también muestra openjdk:17-jdk-slim)
WORKDIR /app
COPY target/*.jar app.jar           # copia el JAR ya compilado
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
Build de la imagen (primero compilás, después construís):
```bash
mvnw clean package -DskipTests       # genera el JAR en target/
docker build -t springboot-api .
```

### 6.4 docker-compose.yml (ambos servicios de una)
Esta es **la forma limpia**: un solo archivo levanta DB + API en la misma red.
```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8
    container_name: my-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: mydb
      MYSQL_USER: user
      MYSQL_PASSWORD: pass
    ports:
      - "3306:3306"
    networks:
      - my-network

  springboot:
    build: .                  # usa el Dockerfile de la raíz
    container_name: spring-api
    ports:
      - "8080:8080"
    depends_on:
      - mysql                 # arranca después de mysql
    networks:
      - my-network

networks:
  my-network:
    driver: bridge
```
```bash
docker compose up --build -d     # levantar todo
docker compose down              # bajar todo
docker logs spring-api           # ver logs de la API
```

> **Gotcha de `depends_on`:** garantiza el ORDEN de arranque, NO que MySQL esté
> "listo para conexiones". MySQL tarda unos segundos en aceptar conexiones; tu
> app debe reintentar la conexión (o usás un healthcheck). Si la app casca al
> arranque, suele ser esto.

---

## 7. Orquestación — Kubernetes (K8s)

### Concepto
Docker **crea** contenedores; Kubernetes los **administra en producción**: escalado, red, reinicios automáticos, balanceo. Es el siguiente escalón cuando ya tenés imágenes Docker funcionando.

**Vocabulario:**
| Concepto | Qué es |
|----------|--------|
| **Pod** | Unidad mínima (1+ contenedores) |
| **Deployment** | Gestiona réplicas y actualizaciones |
| **Service** | Expone la app (le da acceso de red estable) |
| **Node** | Máquina del cluster |
| **Cluster** | Conjunto de nodos |

### Flujo real en producción
```
1. Construís imagen Docker
2. La subís a un registry (Docker Hub, ECR, GCR)
3. Kubernetes la descarga desde ahí
4. Se despliega en la nube (GKE / EKS / AKS)
```
Local: Docker Desktop (Settings → Kubernetes → Enable) o Minikube + `kubectl`.

### Deployment (réplicas)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mi-app
spec:
  replicas: 2                  # ← 2 copias corriendo
  selector:
    matchLabels:
      app: mi-app
  template:
    metadata:
      labels:
        app: mi-app
    spec:
      containers:
      - name: mi-app
        image: mi-app:1.0
        ports:
        - containerPort: 3000
```
```bash
kubectl apply -f deployment.yaml
kubectl get pods
```

### Service (exponer)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: mi-app-service
spec:
  type: NodePort
  selector:
    app: mi-app               # conecta con los pods que tengan este label
  ports:
    - port: 80
      targetPort: 3000        # puerto del contenedor
      nodePort: 30007         # puerto accesible desde fuera
```
```bash
kubectl apply -f service.yaml
kubectl get services
# Acceso: http://localhost:30007
```

### Escalar
```bash
kubectl scale deployment mi-app --replicas=5
kubectl get pods    # K8s crea los pods que falten automáticamente
```

> **El `selector.matchLabels` es el pegamento.** El Service encuentra a los Pods
> por sus `labels`. Si los labels del Deployment y el selector del Service no
> coinciden, el Service no enruta a nada (y no te avisa con un error obvio).

---

## 8. Checklist de REFACTOR (usá esto sobre el otro proyecto)

Recorré el proyecto a refactorizar y chequeá:

**Estructura y código**
- [ ] ¿Las dependencias se inyectan **por constructor** y son `final`? (no `@Autowired` en campos)
- [ ] ¿Hay separación clara `controller / service / repository / model`?
- [ ] ¿Los controllers usan `ResponseEntity` para devolver 200/404/204 correctos?
- [ ] ¿Las entidades JPA se exponen directo en la API? → considerá meter **DTOs**.

**Comunicación entre servicios**
- [ ] ¿Hay URLs de otros servicios **hardcodeadas** (`http://localhost:8081`)? → migrá a **Feign + Eureka por nombre**.
- [ ] ¿Hay llamadas síncronas que en realidad podrían ser **eventos asíncronos** (RabbitMQ)? (notificaciones, emails, tareas pesadas)
- [ ] ¿Los consumers RabbitMQ son **idempotentes**? ¿Hay **DLQ**?

**Compatibilidad de versiones**
- [ ] ¿La versión de **Spring Cloud** está atada a la de **Spring Boot** vía BOM en `<dependencyManagement>`? (no versiones sueltas por starter)

**Contenedores**
- [ ] ¿Hay **Dockerfile**? ¿Copia el JAR y expone el puerto correcto?
- [ ] ¿La conexión a la DB usa el **nombre del contenedor** como host (no `localhost`) cuando corre en Docker?
- [ ] ¿Hay **volumen** para persistir datos de la base?
- [ ] ¿Existe un **docker-compose.yml** que levante app + DB en la misma **red**?
- [ ] ¿La app **reintenta** la conexión a la DB al arrancar? (por el `depends_on` que no espera a que MySQL esté listo)

**Orquestación (si aplica)**
- [ ] ¿La imagen se publica a un **registry**?
- [ ] ¿Hay `Deployment` + `Service` con **labels/selector coincidentes**?

---

## 9. Comandos de referencia rápida

```bash
# --- Docker ---
docker build -t mi-app .                       # construir imagen
docker run -d -p 8080:8080 --name api mi-app   # correr en background
docker network create my-network               # crear red
docker exec -it <cont> bash                     # entrar al contenedor
docker logs <cont>                              # ver logs
docker compose up --build -d                    # levantar stack completo
docker compose down                             # bajar stack

# --- Maven ---
mvnw clean package -DskipTests                  # compilar (genera target/*.jar)

# --- Kubernetes ---
kubectl apply -f deployment.yaml                # aplicar manifiesto
kubectl get pods / get services                 # listar recursos
kubectl scale deployment mi-app --replicas=5    # escalar
```

---

*Generado a partir de los laboratorios 1-1 (Feign), 2-1 (RabbitMQ), 3-2 (Eureka),
6-1 (Docker/MySQL), 6-3 (Backend en contenedor) y 6-5 (Kubernetes).*
