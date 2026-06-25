# Buenas Prácticas — API REST con Spring Boot

> Patrones extraídos del proyecto **`Api-Rest`** (farmacia: Medicamento + Laboratorio).
> Este proyecto es bastante más maduro que los labs del curso: tiene **DTOs**,
> **CRUD genérico reutilizable**, **respuesta uniforme**, **manejo global de
> excepciones** y **validación con i18n**. Esto es lo que querés llevarte al
> refactor.
>
> Complementa a `GUIA-Microservicios-SpringBoot.md`. Aquel cubre la arquitectura
> distribuida (Feign, Eureka, RabbitMQ, Docker, K8s); este cubre **cómo se
> estructura UN servicio por dentro, bien hecho**.

---

## 0. La idea central: programar contra CONTRATOS, no contra implementaciones

El valor de este proyecto no son las clases sueltas, es cómo se **separan responsabilidades** y cómo cada capa habla con la otra por un contrato claro:

```
HTTP request
   │
   ▼
┌─────────────┐   recibe/devuelve DTO (nunca la entidad cruda)
│ Controller  │   valida (@Valid) · arma ApiResponse · setea HTTP status
└──────┬──────┘
       │ DTO ──(ModelMapper)──► Entity
       ▼
┌─────────────┐   lógica de negocio · reglas (ej: "nombre no duplicado")
│  Service    │   extiende ICRUDImpl (CRUD genérico gratis)
└──────┬──────┘
       ▼
┌─────────────┐   acceso a datos (Spring Data JPA)
│ Repository  │   derived queries: findByNombre, existsByNombre
└──────┬──────┘
       ▼
   Base de datos

   ⟂ Atravesando todo:
     · @RestControllerAdvice → traduce excepciones a HTTP (400/404/409/500)
     · ApiResponse<T>        → formato de respuesta uniforme
     · messages.properties   → mensajes de validación centralizados (i18n)
```

Cada práctica de abajo es una pieza de este diagrama.

---

## 1. Separar DTO de Entity (y mapear con ModelMapper)

### Por qué
La **entidad** es tu modelo de persistencia (cómo se guarda en la DB). El **DTO** es tu contrato de API (qué entra y sale por HTTP). Si los mezclás:
- Exponés la estructura interna de la base al cliente.
- Cualquier cambio en la tabla rompe tu API.
- Tenés que cargar relaciones que el cliente no pidió (o caer en loops de serialización).

> Esto es justo lo que en `GUIA-Microservicios-SpringBoot.md` marqué como "mejor
> práctica" sobre los labs (que exponían la `@Entity` directo). Acá **ya está
> bien hecho**.

### Patrón (del proyecto)
**Entity** — anotaciones JPA, mapeo a tabla/columnas reales:
```java
@Data
@Entity
@Table(name = "tb_medicamento")
public class Medicamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_med")
    private Integer codigo;
    @Column(name = "nom_med")
    private String nombre;
    @Column(name = "stock_med")
    private int stock;
    @Column(name = "pre_med")
    private double precio;
    @Column(name = "fec_ven_med")
    private LocalDate fechaVencimiento;

    @ManyToOne
    @JoinColumn(name = "cod_lab")
    private Laboratorio laboratorio;
}
```

**DTO** — solo lo que viaja por la API, con validaciones:
```java
@Data
public class MedicamentoDTO {
    private Integer codigo;
    @NotEmpty(message = "{med.nombre.empty}")
    private String nombre;
    @Min(value = 1,   message = "{med.stock.min}")
    @Max(value = 100, message = "{med.stock.max}")
    private int stock;
    private double precio;
    private LocalDate fechaVencimiento;
    private LaboratorioDTO laboratorio;   // ← DTO anidado, no la entidad
}
```

**Bean de ModelMapper** (una sola vez):
```java
@Configuration
public class MapperConfig {
    @Bean
    public ModelMapper modelo() {
        return new ModelMapper();
    }
}
```

**Conversión en el controller** (en ambos sentidos):
```java
// Entity → DTO (al devolver)
MedicamentoDTO dto = mapper.map(medicamento, MedicamentoDTO.class);

// DTO → Entity (al recibir)
Medicamento bean = mapper.map(dto, Medicamento.class);

// Lista completa con streams
List<MedicamentoDTO> datos = medServicio.listar().stream()
        .map(b -> mapper.map(b, MedicamentoDTO.class))
        .collect(Collectors.toList());
```

---

## 2. CRUD genérico reutilizable (`ICRUD` + `ICRUDImpl`)

### Por qué
Sin esto, escribís `registrar/actualizar/eliminar/listar/buscar` IGUAL en cada servicio. Con un CRUD genérico, lo escribís **una vez** y cada servicio nuevo lo hereda. Menos código repetido = menos lugares donde meter bugs.

### Patrón (del proyecto)
**1) El contrato genérico** (`T` = entidad, `ID` = tipo de la PK):
```java
public interface ICRUD<T, ID> {
    T registrar(T bean) throws Exception;
    T actualizar(T bean) throws Exception;
    void eliminar(ID cod) throws Exception;
    List<T> listar() throws Exception;
    T buscarPorCodigo(ID cod) throws Exception;
}
```

**2) La implementación abstracta** — implementa todo apoyándose en un `repo()` abstracto que cada hijo provee:
```java
public abstract class ICRUDImpl<T, ID> implements ICRUD<T, ID> {

    public abstract JpaRepository<T, ID> repo();   // cada servicio dice cuál es su repo

    @Override public T registrar(T bean)  throws Exception { return repo().save(bean); }
    @Override public T actualizar(T bean) throws Exception { return repo().save(bean); }
    @Override public List<T> listar()     throws Exception { return repo().findAll(); }

    @Override public void eliminar(ID cod) throws Exception {
        if (!repo().existsById(cod))
            throw new ModeloNotFoundException("Registro con código " + cod + " no existe");
        repo().deleteById(cod);
    }

    @Override public T buscarPorCodigo(ID cod) throws Exception {
        return repo().findById(cod)
                .orElseThrow(() -> new ModeloNotFoundException("Registro con código " + cod + " no existe"));
    }
}
```

**3) El servicio concreto** — hereda el CRUD y solo agrega lo específico:
```java
@Service
public class MedicamentoServices extends ICRUDImpl<Medicamento, Integer> {

    @Autowired
    private MedicamentoRepository repo;

    @Override
    public JpaRepository<Medicamento, Integer> repo() {
        return repo;     // ← única "config" que necesita el genérico
    }

    // métodos PROPIOS de este dominio:
    public Medicamento consultaPorNombre(String nom) { return repo.findByNombre(nom); }
    public boolean existePorNombre(String nom)        { return repo.existsByNombre(nom); }
}
```

> **Cómo aplicarlo al refactor:** si ves servicios que repiten el mismo
> `findAll/save/findById/deleteById`, extraé un `ICRUDImpl` genérico y hacé que
> todos extiendan de él. El día que cambies la lógica de "eliminar", la cambiás
> en UN lugar.

---

## 3. Respuesta uniforme: `ApiResponse<T>`

### Por qué
Si cada endpoint devuelve un formato distinto (a veces el objeto pelado, a veces un texto, a veces un mapa), el front sufre. Un **envoltorio único** hace toda la API predecible: el cliente siempre sabe dónde mirar.

### Patrón (del proyecto)
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;   // ¿salió bien?
    private String  mensaje;   // texto legible
    private T       data;      // el payload (genérico)
}
```

Uso en el controller, siempre con el `HttpStatus` correcto:
```java
// 200 OK
ApiResponse<List<MedicamentoDTO>> response =
        new ApiResponse<>(true, "Listado de medicamentos", datos);
return new ResponseEntity<>(response, HttpStatus.OK);

// 201 CREATED al registrar
return new ResponseEntity<>(
        new ApiResponse<>(true, "Medicamento registrado", medDTO),
        HttpStatus.CREATED);
```

Respuesta JSON consistente para TODO endpoint:
```json
{
  "success": true,
  "mensaje": "Medicamento registrado",
  "data": { "codigo": 1, "nombre": "Paracetamol", "stock": 50 }
}
```

---

## 4. Manejo GLOBAL de excepciones (`@RestControllerAdvice`)

### Por qué
Esta es, lejos, **la mejor pieza del proyecto**. En vez de llenar cada controller de `try/catch`, centralizás el manejo de errores en un solo lado y mapeás cada tipo de excepción a su **código HTTP correcto**. Los controllers quedan limpios: solo lanzan excepciones de negocio.

### Patrón (del proyecto)
**Excepciones de dominio** (heredan de `RuntimeException`):
```java
public class ModeloNotFoundException extends RuntimeException {
    public ModeloNotFoundException(String mensaje) { super(mensaje); }
}

public class BusinessException extends RuntimeException {
    public BusinessException(String mensaje) { super(mensaje); }
}
```

**El handler global** — un solo lugar, cada excepción → su status:
```java
@RestControllerAdvice
public class ValidationHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidationHandler.class);

    // 400 — errores de validación de @Valid, con detalle por campo
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationErrors(MethodArgumentNotValidException e) {
        Map<String, String> errores = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(err ->
            errores.put(err.getField(), err.getDefaultMessage()));
        return new ResponseEntity<>(
            new ApiResponse<>(false, "Errores de validación", errores),
            HttpStatus.BAD_REQUEST);
    }

    // 404 — recurso no encontrado
    @ExceptionHandler(ModeloNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> manejarNotFound(ModeloNotFoundException ex) {
        return new ResponseEntity<>(
            new ApiResponse<>(false, ex.getMessage(), null), HttpStatus.NOT_FOUND);
    }

    // 409 — conflicto de regla de negocio (ej: nombre duplicado)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusiness(BusinessException ex) {
        return new ResponseEntity<>(
            new ApiResponse<>(false, ex.getMessage(), null), HttpStatus.CONFLICT);
    }

    // 500 — cualquier otra. Se LOGUEA el detalle, NO se expone al cliente
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
        log.error("Error interno no controlado", ex);     // ← detalle solo en el log
        return new ResponseEntity<>(
            new ApiResponse<>(false, "Error interno del servidor", null),
            HttpStatus.INTERNAL_SERVER_ERROR);            // ← mensaje genérico al cliente
    }
}
```

### Tabla de mapeo (memorizala — es el contrato HTTP)
| Situación | Excepción | HTTP |
|-----------|-----------|------|
| Falla validación de `@Valid` | `MethodArgumentNotValidException` | **400** Bad Request |
| Recurso no existe | `ModeloNotFoundException` | **404** Not Found |
| Regla de negocio violada (duplicado, etc.) | `BusinessException` | **409** Conflict |
| Cualquier otro error inesperado | `Exception` | **500** Internal Server Error |

> **Por qué el 500 no expone el detalle:** filtrar el stacktrace o el mensaje
> interno al cliente es un **riesgo de seguridad** (revela estructura, queries,
> rutas). Se loguea internamente y al cliente le llega un mensaje genérico. Eso
> está bien resuelto acá.

---

## 5. Validación declarativa + mensajes i18n

### Por qué
Validar a mano (`if (nombre == null || nombre.isEmpty())`) ensucia el código y es propenso a olvidos. Bean Validation lo hace **declarativo** sobre el DTO, y los mensajes se centralizan en `messages.properties` (traducibles, reutilizables).

### Patrón (del proyecto)
**1) Anotaciones en el DTO**, con claves de mensaje entre llaves:
```java
@NotEmpty(message = "{med.nombre.empty}")
private String nombre;

@Min(value = 1,   message = "{med.stock.min}")
@Max(value = 100, message = "{med.stock.max}")
private int stock;
```

**2) Mensajes en `src/main/resources/messages.properties`:**
```properties
med.nombre.empty=Campo nombre no puede estar vacio
med.stock.min=Campo stock MIN: 1
med.stock.max=Campo stock MAX:100
```

**3) Registrar el basename en `application.properties`:**
```properties
spring.messages.basename=messages
```

**4) Disparar la validación con `@Valid` en el controller:**
```java
@PostMapping("/registrar")
public ResponseEntity<ApiResponse<?>> registrar(@Valid @RequestBody MedicamentoDTO med) {
    ...
}
```
Si falla, salta `MethodArgumentNotValidException` → la atrapa el handler global → 400 con el detalle por campo. **Todo conectado.**

Dependencia necesaria: `spring-boot-starter-validation`.

---

## 6. Mapeo de entidades y relaciones

### Convenciones del proyecto
- **Nombres de tabla y columnas explícitos**, en `snake_case`, desacoplados del Java (`camelCase`):
  ```java
  @Table(name = "tb_medicamento")
  @Column(name = "cod_med") private Integer codigo;
  ```
  Así el nombre en Java puede ser claro sin atarte al nombre físico de la DB.

- **Relaciones bidireccionales + corte de serialización con `@JsonIgnore`:**
  ```java
  // Lado "muchos"
  @ManyToOne
  @JoinColumn(name = "cod_lab")
  private Laboratorio laboratorio;

  // Lado "uno"
  @JsonIgnore                          // ← evita el loop infinito al serializar
  @OneToMany(mappedBy = "laboratorio")
  private List<Medicamento> listaMedicamentos;
  ```

> **Gotcha clásico:** sin `@JsonIgnore` (o `@JsonManagedReference/@JsonBackReference`),
> serializar `Laboratorio` → lista de `Medicamento` → cada uno con su `Laboratorio`
> → ... = **StackOverflow / loop infinito**. Acá está cortado en el lado `@OneToMany`.
> Si usás DTOs en todo (sección 1), este problema casi ni aparece.

---

## 7. Repositorios: derived queries primero, `@Query` solo si hace falta

### Patrón (del proyecto)
```java
public interface MedicamentoRepository extends JpaRepository<Medicamento, Integer> {

    // Derived queries: Spring genera el SQL a partir del NOMBRE del método
    Medicamento findByNombre(String nom);
    boolean existsByNombre(String nom);

    // HQL explícito solo cuando la consulta es compleja (acá quedó comentado):
    // @Query("select m from Medicamento m where m.nombre = :nom")
    // Medicamento buscarPorNombre(String nom);
}
```

> Preferí los **derived query methods** (`findBy...`, `existsBy...`, `countBy...`)
> por legibilidad. Pasá a `@Query` solo cuando el nombre del método se vuelve
> impronunciable o necesitás joins/proyecciones que el naming no cubre.

---

## 8. Configuración externalizada (no hardcodees credenciales)

### Patrón (del proyecto)
```properties
server.port=8091
spring.jpa.database=MYSQL
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/farmacia_2026_dsw_ii_rest?serverTimezone=UTC
spring.datasource.username=${DB_USER:root}        # ← variable de entorno con default
spring.datasource.password=${DB_PASSWORD:mysql}   # ← idem
spring.messages.basename=messages
```

> **Lo bueno:** `${DB_USER:root}` lee la variable de entorno `DB_USER`; si no
> existe, usa `root`. Esto deja la **misma config corriendo en local y en
> contenedor/prod** sin tocar el archivo: en Docker le pasás `-e DB_USER=...`.
> Conecta directo con la sección de Docker de la otra guía.

---

## 9. Stack del proyecto (`pom.xml`)

| Dependencia | Para qué |
|-------------|----------|
| `spring-boot-starter-data-jpa` | Persistencia / Spring Data |
| `spring-boot-starter-webmvc` | API REST (web MVC) |
| `spring-boot-starter-validation` | Bean Validation (`@Valid`, `@NotEmpty`...) |
| `modelmapper` (2.4.2) | Mapeo automático DTO ↔ Entity |
| `lombok` | `@Data`, constructores, getters/setters sin boilerplate |
| `mysql-connector-j` | Driver MySQL (runtime) |
| `spring-boot-devtools` | Hot reload en desarrollo (runtime, opcional) |

Java 17 · empaquetado con `spring-boot-maven-plugin`.

---

## 10. Callouts honestos — lo que MEJORARÍA al refactorizar

Vos lo llamás "buenas prácticas" y la **arquitectura** lo es. Pero el proyecto tiene inconsistencias reales. Si lo tomás de molde, corregí esto:

1. **Inyección por campo (`@Autowired`) en vez de por constructor.**
   Los servicios y controllers usan `@Autowired` sobre el campo:
   ```java
   @Autowired
   private MedicamentoServices medServicio;   // ❌ field injection
   ```
   Es la práctica MENOS recomendada: no podés marcar el campo `final`, es más
   difícil de testear y oculta dependencias. La otra guía (`GUIA-Microservicios-SpringBoot.md`,
   sección 1.2) usa inyección por constructor, que es lo correcto. **El proyecto
   se contradice a sí mismo.** Migrá a constructor (con Lombok, `@RequiredArgsConstructor`
   lo hace gratis).

2. **Validaciones redundantes (código muerto) en el controller.**
   El controller hace:
   ```java
   Medicamento med = medServicio.buscarPorCodigo(cod);
   if (med == null) throw new ModeloNotFoundException(...);   // ❌ nunca se cumple
   ```
   Pero `buscarPorCodigo` (sección 2) **ya lanza** `ModeloNotFoundException` con
   `orElseThrow` — nunca devuelve `null`. Ese `if` es inalcanzable. Confía en el
   service: sacá el chequeo del controller.

3. **`throws Exception` genérico en las firmas del CRUD.**
   `T registrar(T bean) throws Exception` es demasiado amplio: obliga a propagar
   `Exception` por todos lados y tapa qué puede fallar de verdad. Mejor lanzar
   excepciones específicas (las de negocio que ya tenés) sin declarar `Exception`.

4. **Validaciones también en la Entity.**
   `Medicamento` tiene anotaciones de Bean Validation además del DTO. La
   validación de entrada va en el **DTO** (es el borde de la API). En la entidad,
   solo invariantes de persistencia. Tenerlas duplicadas confunde sobre quién
   valida qué.

5. **`ApiResponse` sin timestamp.**
   Tiene un `// private LocalDateTime fecha;` comentado. Para trazabilidad y debug,
   un `fecha` (y opcionalmente un `path`/código de error) en las respuestas de
   error ayuda bastante. Descomentalo.

6. **ModelMapper 2.4.2** es viejo (la 3.x es la actual). Al refactorizar, actualizá.

---

## 11. Checklist de refactor con estas prácticas

- [ ] ¿Los endpoints reciben/devuelven **DTOs** (no entidades crudas)? ¿Hay un `ModelMapper` configurado?
- [ ] ¿Existe un **CRUD genérico** (`ICRUD` + `ICRUDImpl`) y los servicios lo extienden, en vez de repetir `findAll/save/...`?
- [ ] ¿Todas las respuestas usan un **wrapper uniforme** (`ApiResponse<T>`) con su `HttpStatus` correcto (200/201/204)?
- [ ] ¿Hay un **`@RestControllerAdvice` global** que mapee excepciones a 400/404/409/500?
- [ ] ¿El error 500 **loguea** el detalle pero **no** lo expone al cliente?
- [ ] ¿La validación es **declarativa** (`@Valid` + anotaciones) con mensajes en `messages.properties`?
- [ ] ¿Las relaciones JPA cortan el loop de serialización (`@JsonIgnore` o DTOs)?
- [ ] ¿Las credenciales salen de **variables de entorno** con default (`${DB_USER:root}`)?
- [ ] **Corrección:** ¿La inyección es **por constructor** (no `@Autowired` en campos)?
- [ ] **Corrección:** ¿Sacaste los chequeos `if (x == null)` redundantes cuando el service ya lanza la excepción?

---

*Generado a partir del análisis del proyecto `Api-Rest` (Medicamento + Laboratorio):
controllers, services, DTOs, entities, utils (ApiResponse / excepciones /
ValidationHandler), MapperConfig, repositories, application.properties,
messages.properties y pom.xml.*
