---
marp: true
paginate: true
size: 16:9
footer: 'Krypton — Migración a Microservicios · CIBERTEC DAW II'
style: |
  @import url('https://fonts.googleapis.com/css2?family=Kanit:ital,wght@0,300;0,400;0,500;0,600;0,800;1,800&display=swap');
  :root {
    --kr-navy:#03275A; --kr-navy-900:#021a3d; --kr-blue:#1A7DD7;
    --kr-yellow:#F2B809; --kr-orange:#F37402; --kr-redorange:#F34605;
    --kr-body:#313c54; --kr-muted:#6b7890; --kr-page:#f5f7fa;
  }
  section {
    font-family:'Kanit',system-ui,sans-serif;
    background:var(--kr-page); color:var(--kr-body);
    font-size:26px; padding:78px 84px; line-height:1.42;
    justify-content:flex-start;
  }
  section::before{
    content:''; position:absolute; top:0; left:0; right:0; height:10px;
    background:linear-gradient(90deg,var(--kr-navy),var(--kr-blue),var(--kr-yellow),var(--kr-orange),var(--kr-redorange));
  }
  h1{ font-family:'Kanit'; font-weight:800; font-style:italic; color:var(--kr-navy);
      font-size:2em; letter-spacing:-0.02em; margin:0 0 .35em; line-height:1.04; }
  h2{ font-weight:600; color:var(--kr-blue); font-size:1.2em; margin:.1em 0 .55em; }
  ul,ol{ margin-top:.25em; }
  li{ margin:.4em 0; }
  li::marker{ color:var(--kr-orange); }
  strong{ color:var(--kr-navy); font-weight:700; }
  code{ background:#eef6fe; color:var(--kr-navy); padding:.05em .35em; border-radius:5px; font-size:.9em; }
  a{ color:var(--kr-blue); }
  section::after{ color:var(--kr-muted); font-weight:600; }
  footer{ color:var(--kr-muted); font-weight:600; font-size:.6em; letter-spacing:.04em; }
  /* Portada / cierre / divisores sobre navy */
  section.lead{ background:radial-gradient(130% 130% at 0% 0%,var(--kr-navy) 0%,var(--kr-navy-900) 72%);
    color:#fff; justify-content:center; text-align:center; }
  section.lead h1{ color:#fff; font-size:3.1em; }
  section.lead h2{ color:var(--kr-yellow); font-weight:600; }
  section.lead strong{ color:var(--kr-yellow); }
  section.lead p{ color:#d7ebfb; font-size:.92em; margin:.2em 0; }
---

<!-- _class: lead -->
<!-- _paginate: false -->
<!-- _footer: '' -->

# Krypton

## Migración de un Monolito a Microservicios

CIBERTEC · Desarrollo de Aplicaciones Web II — EFSRT VI

**Tayra · Jason · Curi · Cristofer · Sergio**

<!--
🎤 Tayra: «Buenas a todos, somos el equipo de Krypton. Yo soy Tayra y, junto con Jason, Curi, Cristofer y Sergio, les vamos a mostrar cómo tomamos un e-commerce que funcionaba como un monolito y lo convertimos en nueve microservicios. Es nuestro proyecto de CIBERTEC, para Desarrollo de Aplicaciones Web II.»
-->

---

# Agenda

1. Qué es Krypton
2. Por qué microservicios
3. La arquitectura
4. Cada servicio y patrón
5. Demo en vivo
6. Conclusiones

<!--
🎤 Tayra: «En los próximos minutos vamos a recorrer seis cosas: qué es Krypton; por qué pasamos a microservicios; cómo quedó la arquitectura; cada servicio y patrón que usamos; una demo en vivo; y las conclusiones.»
→ Traspaso: «Para arrancar, le dejo la palabra a Curi.»
-->

---

# El proyecto Krypton

- E-commerce B2C de tecnología
- Monolito **Spring Boot 3 + Java 17**
- Frontend **React 19** · **MySQL 8**
- Catálogo, carrito, checkout, órdenes, admin

<!--
🎤 Curi: «Krypton es una tienda online de tecnología. Tiene catálogo, carrito, checkout, órdenes y panel de administración. Backend en Spring Boot y Java 17, frontend React 19, base MySQL. Importante: la app YA funcionaba como monolito, con más de 400 tests en verde. No reescribimos la lógica; la repartimos.»
-->

---

# ¿Por qué microservicios?

- Monolito: **todo en un proceso**
- No escala por partes · un fallo afecta todo
- Despliegue acoplado
- → cada parte **independiente**, su base, escala sola
- Patrones: **Eureka · Feign · RabbitMQ · Docker**

<!--
🎤 Curi: «En un monolito está todo en un solo proceso: no escalás solo la parte que más se usa, si una parte falla puede caerse todo, y desplegás la app entera por cualquier cambio. Con microservicios cada parte es independiente, tiene su base, escala y se despliega sola. Queremos demostrar cuatro patrones: Eureka, Feign, RabbitMQ y Docker.»
-->

---

# Arquitectura general

- Frontend → **api-gateway (:8080)**
- 9 servicios, todos en **Eureka (:8761)**
- **1 base MySQL por servicio** + RabbitMQ
- 3 ideas: 1 base/servicio · se llaman por **nombre** · **JWT = pasaporte**

> 📊 Visual: insertar acá el diagrama de arquitectura (README).

<!--
🎤 Curi: «Esta es la foto completa. El frontend habla con el api-gateway en el 8080. El gateway reparte a los nueve servicios, todos registrados en Eureka. Cada servicio tiene su base MySQL y tenemos RabbitMQ para los mensajes. Tres ideas que se repiten: una base por servicio, se llaman por nombre, y el JWT es el pasaporte. A partir de acá, arranca Tayra con la infraestructura.»
-->

---

# Service Discovery — Eureka

- Las IPs/puertos cambian: **no se hardcodean**
- Eureka = **registro vivo** (la "guía telefónica")
- Cada servicio se anota al arrancar
- Los demás lo buscan **por nombre**

<!--
🎤 Tayra: «Gracias, Curi. Eureka es como una guía telefónica: en vez de memorizarte el número (la IP), buscás por nombre. El eureka-server es un registro vivo: cada servicio se anota al arrancar, y los demás lo buscan por nombre, no por una IP fija que además cambia.»
-->

---

# API Gateway

- Una **sola puerta** (`:8080`)
- Rutea por nombre: `lb://users-service`
- Resuelve con Eureka **+ balancea**
- Centraliza CORS · el front no sabe los puertos

<!--
🎤 Tayra: «Si hay nueve servicios, ¿el front conoce nueve puertos? No. El gateway es la única puerta, el 8080. El navegador habla solo con él y él reparte. El "lb://" significa: resolvé por Eureka y balanceá. El front nunca se entera de los puertos internos.»
-->

---

# Dockerización

- Un **Dockerfile** por servicio
- `docker compose up` → **11 contenedores**
- Parametrizado por **env**: `localhost` → nombre de contenedor

<!--
🎤 Tayra: «Empaquetamos cada servicio en una imagen con su Dockerfile. Con un docker compose up levantamos MySQL, RabbitMQ, los nueve servicios y el gateway: once contenedores en una línea. El truco: las direcciones son configurables; en Docker se hablan por el nombre del contenedor.»
→ Traspaso: «Ahora Jason les cuenta la seguridad.»
-->

---

# Login: el flujo

- Front → `POST /api/auth/login`
- **users** verifica password (BCrypt)
- **Emite el JWT** → el front lo guarda
- Se manda en **cada request**

<!--
🎤 Jason: «Gracias, Tayra. Todo empieza en el login: el usuario manda email y contraseña al users-service. Verifica la contraseña (guardada con BCrypt) y, si está bien, emite un JWT: el pasaporte del usuario. El front lo guarda y lo manda en cada pedido.»
-->

---

# ¿Qué es un JWT?

- `header.payload.signature`
- header = algoritmo · payload = **email + rol + exp**
- signature = **firma**
- **Firmado, NO encriptado** (se lee, no se modifica)

<!--
🎤 Jason: «El token tiene tres partes: header, payload y firma. El header dice el algoritmo; el payload lleva email, rol y expiración; la firma garantiza que nadie lo tocó. Clave: está FIRMADO, no encriptado. Cualquiera lo lee, pero sin el secreto nadie puede falsificar la firma.»
-->

---

# JWT stateless

- **users FIRMA** con el secreto
- los demás **VERIFICAN** con el MISMO secreto
- sin sesión, sin DB, sin preguntar a users
- secreto compartido = el **"contrato"**

<!--
🎤 Jason: «Acá está por qué escala. users firma con un secreto; todos los demás verifican con el MISMO secreto. Cada servicio valida por su cuenta, sin sesión ni base de datos. El secreto compartido es el contrato: mismo secreto, misma firma, confianza. Y el secreto nunca viaja en el token.»
→ Traspaso: «Le paso a Curi con el catálogo y las reseñas.»
-->

---

# catalog-service

- Dueño del catálogo: **productos, categorías, stock**
- base `krypton_catalog`
- Valida un JWT que **NO emitió** (stateless)
- Expone el **stock** para el checkout

<!--
🎤 Curi: «Vuelvo yo. El catalog-service es dueño de productos, categorías y stock, con su base krypton_catalog. No tiene la tabla de usuarios, pero valida el token igual, con el secreto compartido. Y guarda el stock, que el checkout va a necesitar para descontar.»
-->

---

# review-service — Reseñas

- Rating **1–5** + comentario · base `krypton_reviews`
- Promedio con **AVG**
- Valida que el producto exista **vía Feign → catalog**
- 1 reseña por usuario por producto

<!--
🎤 Curi: «El servicio de reseñas: rating del 1 al 5 con comentario, en krypton_reviews. Calcula el promedio con un AVG. Antes de aceptar una reseña le pregunta a catalog, por Feign, si el producto existe. Y solo deja una reseña por usuario por producto.»
→ Traspaso: «Ahora Cristofer con el checkout.»
-->

---

# El problema del checkout

- El checkout debe **descontar stock**
- …pero el stock vive en **catalog** (otra base, otro proceso)
- **No hay `@Transactional`** entre servicios
- ¿Cómo garantizo **"todo o nada"**?

<!--
🎤 Cristofer: «Gracias, Curi. El corazón del proyecto: el checkout. Al comprar hay que descontar stock, pero el stock vive en catalog: otro servicio, otra base. No existe un @Transactional que cruce dos servicios. En un monolito era una sola transacción; acá no. ¿Cómo garantizo todo o nada?»
-->

---

# Feign + Saga con compensación

- **Feign** = llamar a otro servicio como un método (síncrono)
- order pide a catalog: **descontar** stock
- Si algo falla después → **compensación**
- order pide a catalog: **restaurar** stock (rollback a mano)

<!--
🎤 Cristofer: «Dos piezas. Feign nos deja llamar a otro servicio como si fuera un método nuestro: order le pide a catalog que descuente stock. Y la saga con compensación: descuento primero; si después falla la orden, lo deshago a mano pidiéndole a catalog que RESTAURE el stock. Ese es el rollback distribuido. Lo probamos con tests: feliz, sin stock, y compensación.»
-->

---

# Cupón de descuento

- `couponCode` en el checkout
- order pide el descuento a **promo** (Feign)
- `total = subtotal − descuento + envío`
- El descuento es **REAL**: lo decide el backend

<!--
🎤 Cristofer: «Otra llamada Feign en el checkout: cuando hay cupón, order le pregunta a promo cuánto descuenta. El total es subtotal menos descuento más envío. Y lo importante: el descuento es real, lo decide el backend; el front solo muestra una vista previa.»
→ Traspaso: «Ahora Sergio con la mensajería, el pago y las promos.»
-->

---

# RabbitMQ — Mensajería asíncrona

- Síncrono (Feign) = **llamada telefónica**
- Asíncrono (RabbitMQ) = **WhatsApp**
- order **publica** "orden creada" → notification **consume**
- Desacople total

<!--
🎤 Sergio: «Gracias, Cristofer. La otra forma de comunicarse. Feign es como una llamada: esperás. RabbitMQ es como un WhatsApp: mandás y seguís. Cuando se crea una orden, order publica el evento y sigue; notification lo consume por su cuenta. order ni se entera de quién escucha, y puedo sumar consumidores sin tocarlo.»
-->

---

# payment-service

- `POST /api/orders/{id}/pay`
- order delega el cobro a **payment** (Feign)
- **Máquina de estados**: PENDIENTE → CONFIRMADA
- `402` si se rechaza · `422` si la transición no vale

<!--
🎤 Sergio: «El pago vive en su servicio. Al pagar, order le delega el cobro a payment por Feign. Usamos una máquina de estados: la orden pasa de PENDIENTE a CONFIRMADA solo si se aprueba. Si se rechaza, 402; si alguien intenta una transición inválida (pagar dos veces), 422.»
-->

---

# promo-service

- Cupones: **PORCENTAJE** o **MONTO**
- Admin **crea / lista**
- `POST /api/promos/apply` calcula el descuento
- base `krypton_promos`

<!--
🎤 Sergio: «El servicio de cupones: por porcentaje o monto fijo. El admin los crea y lista, y cuando se aplica uno en el checkout, calcula el descuento. Por ejemplo, 10% sobre 2799 soles son casi 280 menos. Es el servicio que mencionaba Cristofer con el cupón.»
→ Traspaso: «Le paso a Jason para el tramo final.»
-->

---

# Frontend conectado

- **React 19 + Vite** · todo pega al gateway (`:8080`)
- Nuevo: **reseñas** en el producto
- **Cupón** en el checkout · **pago** de la orden
- **Panel admin** de cupones

<!--
🎤 Jason: «Vuelvo yo. El front es React 19 con Vite y todo pega contra el gateway, el mismo 8080: no cambió de puerta. Le sumamos las pantallas nuevas: reseñas en el producto, cupón en el checkout, pago de la orden y el panel admin de cupones.»
-->

---

# Calidad y verificación

- TDD donde aporta: **saga, stock, pagos, cupón** (verdes)
- Verificación con **curls reales** + chequeo en DB
- Todo **containerizado**

<!--
🎤 Jason: «No confiamos en el "debería andar". Cada fase la verificamos con requests reales y mirando la base. Lo crítico —la saga, el stock, los pagos— con tests automatizados en verde. Y todo corre containerizado.»
→ Traspaso: «Le paso a Cristofer para la demo.»
-->

---

# Demo en vivo

**Login → Catálogo → Reseña → Carrito → Checkout con cupón → Pagar → Notificación**

<!--
🎤 Cristofer: «Basta de slides, vamos a verlo. Me logueo, entro al catálogo, dejo una reseña, agrego al carrito, voy al checkout con un cupón, pago, y vemos la notificación en el log.»
⚠️ ANTES: stack levantado, usuario y producto de prueba, log de notification visible. PLAN B: video grabado.
-->

---

# Conclusiones y aprendizajes

- Patrones: discovery, gateway, **JWT stateless**, **Feign + saga**, RabbitMQ, Docker
- Trade-offs: stateless = rápido, pero sin baja inmediata
- Gotchas resueltos (RabbitMQ en Docker, el 401 del error-dispatch)

<!--
🎤 Cristofer: «¿Qué aprendimos? Logramos los patrones que nos propusimos. Pero también que lo distribuido trae problemas nuevos: trade-offs, como que el token stateless es rapidísimo pero no podés dar de baja a alguien al instante. Y varios gotchas que resolvimos. Lo distribuido no es gratis, pero entendimos cómo manejarlo.»
→ Traspaso: «Para cerrar, Sergio.»
-->

---

<!-- _class: lead -->
<!-- _footer: '' -->

# ¡Gracias!

Código y guías en **GitHub**

## ¿Preguntas?

<!--
🎤 Sergio: «Y con esto cerramos. Tomamos un monolito que funcionaba y lo convertimos en nueve microservicios, aplicando los patrones que vimos. El código y las guías están en el repo. ¡Gracias por la atención! ¿Alguna pregunta?»
-->
